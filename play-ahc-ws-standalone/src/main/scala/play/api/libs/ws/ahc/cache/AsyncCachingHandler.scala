/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.time.ZonedDateTime

import play.shaded.ahc.org.asynchttpclient._
import com.typesafe.play.cachecontrol.ResponseServeAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * An async handler that accumulates response data to place in cache with the given key.
 */
class AsyncCachingHandler[T](
    request: Request,
    handler: AsyncCompletionHandler[T],
    cache: AhcHttpCache,
    maybeAction: Option[ResponseServeAction],
    ahcConfig: AsyncHttpClientConfig
) extends AsyncHandler[T]
    with TimeoutResponse
    with Debug {

  private val DATE = "Date"

  import com.typesafe.play.cachecontrol.HttpDate
  import AsyncCachingHandler._

  protected val builder = new CacheableResponseBuilder(ahcConfig)

  protected val requestTime: ZonedDateTime = HttpDate.now

  protected val key: EffectiveURIKey = EffectiveURIKey(request)

  protected val timeout: Duration = scala.concurrent.duration.Duration(1, "second")

  protected lazy val timeoutResponse: CacheableResponse = generateTimeoutResponse(request, ahcConfig)

  /**
   * Invoked if something wrong happened inside the previous methods or when an I/O exception occurs.
   */
  override def onThrowable(t: Throwable): Unit = {
    import com.typesafe.play.cachecontrol.ResponseServeActions._

    maybeAction match {
      case Some(ValidateOrTimeout(reason)) =>
        logger.debug(s"onCompleted: returning timeout because $reason", t)

        // If no-cache or must-revalidate exist, then a
        // successful validation has to happen -- i.e. both stale AND fresh
        // cached responses may not be returned on disconnect.
        // https://tools.ietf.org/html/rfc7234#section-5.2.2.1
        // https://tools.ietf.org/html/rfc7234#section-5.2.2.2
        processTimeoutResponse()

      case other =>
        // If not, then sending a cached response on a disconnect is acceptable
        // as long as 110 and 112 warnings are sent along with it.
        // https://tools.ietf.org/html/rfc7234#section-4.2.4
        logger.debug(s"onCompleted: action = $other", t)
        processDisconnectedResponse()
    }
  }

  /**
   * Called when the status line has been processed.
   */
  override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
    builder.accumulate(responseStatus)
    handler.onStatusReceived(responseStatus)
  }

  /**
   * Called when all response’s headers has been processed.
   */
  override def onHeadersReceived(responseHeaders: HttpHeaders): AsyncHandler.State = {
    if (!responseHeaders.contains(DATE)) {
      /*
       A recipient with a clock that receives a response message without a
       Date header field MUST record the time it was received and append a
       corresponding Date header field to the message's header section if it
       is cached or forwarded downstream.

       https://tools.ietf.org/html/rfc7231#section-7.1.1.2
       */
      val currentDate = HttpDate.format(HttpDate.now)
      responseHeaders.add(DATE, currentDate)
    }
    builder.accumulate(responseHeaders)
    handler.onHeadersReceived(responseHeaders)
  }

  /**
   * Body parts has been received. This method can be invoked many time depending of the response’s bytes body.
   */
  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State = {
    builder.accumulate(bodyPart)
    handler.onBodyPartReceived(bodyPart)
  }

  /**
   * onCompleted: Invoked when the full response has been read, or if the processing get aborted (more on this below).
   */
  override def onCompleted(): T = {
    import com.typesafe.play.cachecontrol.ResponseServeActions._

    if (logger.isTraceEnabled) {
      logger.trace(s"onCompleted: this = $this")
    }

    val response = builder.build
    if (logger.isDebugEnabled) {
      logger.debug(s"onCompleted: response = ${debug(response)}")
    }

    // We got a response.  First, invalidate if unsafe according to
    // https://tools.ietf.org/html/rfc7234#section-4.4
    cache.invalidateIfUnsafe(request, response)

    // "Handling a Validation Response"
    // https://tools.ietf.org/html/rfc7234#section-4.3.3
    if (cache.isNotModified(response)) {
      processNotModifiedResponse(response)
    } else if (cache.isError(response)) {
      // o  However, if a cache receives a 5xx (Server Error) response while
      // attempting to validate a response, it can either forward this
      // response to the requesting client, or act as if the server failed
      // to respond.  In the latter case, the cache MAY send a previously
      // stored response (see Section 4.2.4).

      maybeAction match {
        case Some(Validate(reason, staleIfError)) if staleIfError =>
          processStaleResponse(response)
        case other =>
          processFullResponse(response)
      }
    } else {
      processFullResponse(response)
    }
  }

  protected def processTimeoutResponse(): Unit = {
    handler.onCompleted(timeoutResponse)
  }

  protected def processDisconnectedResponse(): T = {
    logger.debug(s"processDisconnectedResponse:")

    val result        = Await.result(cache.get(key), timeout)
    val finalResponse = result match {
      case Some(entry) =>
        val currentAge        = cache.calculateCurrentAge(request, entry, requestTime)
        val freshnessLifetime = cache.calculateFreshnessLifetime(request, entry)
        val isFresh           = freshnessLifetime.isGreaterThan(currentAge)

        cache.addRevalidationFailed {
          cache.addDisconnectHeader {
            cache.generateCachedResponse(request, entry, currentAge, isFresh = isFresh)
          }
        }

      case None =>
        // Nothing in cache.  Return the timeout.
        timeoutResponse
    }
    handler.onCompleted(finalResponse)
  }

  protected def processStaleResponse(response: CacheableResponse): T = {
    logger.debug(s"processCachedResponse: response = ${debug(response)}")

    val result        = Await.result(cache.get(key), timeout)
    val finalResponse = result match {
      case Some(entry) =>
        val currentAge = cache.calculateCurrentAge(request, entry, requestTime)

        cache.addRevalidationFailed {
          cache.generateCachedResponse(request, entry, currentAge, isFresh = false)
        }

      case None =>
        // Nothing in cache.  Return the error.
        response
    }
    handler.onCompleted(finalResponse)
  }

  protected def processFullResponse(fullResponse: CacheableResponse): T = {
    logger.debug(s"processFullResponse: fullResponse = ${debug(fullResponse)}")
    import com.typesafe.play.cachecontrol.ResponseCachingActions._

    cache.cachingAction(request, fullResponse) match {
      case DoNotCacheResponse(reason) =>
        logger.debug(s"onCompleted: DO NOT CACHE, because $reason")
      case DoCacheResponse(reason) =>
        logger.debug(s"isCacheable: DO CACHE, because $reason")
        cache.cacheResponse(request, fullResponse)
    }
    handler.onCompleted(fullResponse)
  }

  protected def processNotModifiedResponse(notModifiedResponse: CacheableResponse): T = {
    logger.trace(s"processNotModifiedResponse: notModifiedResponse = $notModifiedResponse")

    val result = Await.result(cache.get(key), timeout)
    logger.debug(s"processNotModifiedResponse: result = $result")

    // FIXME XXX Find the response which matches the secondary keys...
    val fullResponse = result match {
      case Some(entry) =>
        val newHeaders    = notModifiedResponse.getHeaders
        val freshResponse = cache.freshenResponse(newHeaders, entry.response)
        cache.cacheResponse(request, freshResponse)
        freshResponse
      case None =>
        notModifiedResponse
    }

    handler.onCompleted(fullResponse)
  }

  override def toString = {
    s"CacheAsyncHandler(key = $key, requestTime = $requestTime, builder = $builder, asyncHandler = ${debug(handler)}})"
  }

}

object AsyncCachingHandler {
  private val logger: Logger = LoggerFactory.getLogger("play.api.libs.ws.ahc.cache.AsyncCachingHandler")
}
