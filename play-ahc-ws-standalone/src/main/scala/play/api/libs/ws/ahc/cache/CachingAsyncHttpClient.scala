/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.io._
import java.util.function.Predicate

import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse, _ }

import scala.concurrent.{ Await, ExecutionContext }

trait TimeoutResponse {

  def generateTimeoutResponse(request: Request, ahcConfig: AsyncHttpClientConfig): CacheableResponse = {
    val uri = request.getUri
    val status = new CacheableHttpResponseStatus(uri, 504, "Gateway Timeout", "")
    val headers = new DefaultHttpHeaders()
    val bodyParts = java.util.Collections.emptyList[CacheableHttpResponseBodyPart]()
    CacheableResponse(status, headers, bodyParts, ahcConfig)
  }
}

/**
 * A provider that pulls a response from the cache.
 */
class CachingAsyncHttpClient(
    underlying: AsyncHttpClient,
    ahcHttpCache: AhcHttpCache)
  extends AsyncHttpClient
  with TimeoutResponse
  with Debug {

  import com.typesafe.play.cachecontrol.ResponseSelectionActions._
  import com.typesafe.play.cachecontrol.ResponseServeActions._
  import com.typesafe.play.cachecontrol._

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val cacheTimeout = scala.concurrent.duration.Duration(1, "second")

  def close(): Unit = {
    underlying.close()
  }

  @throws(classOf[IOException])
  override def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = {
    handler match {
      case asyncCompletionHandler: AsyncCompletionHandler[T] =>
        execute(request, asyncCompletionHandler, null)(ahcHttpCache.executionContext)

      case streamedHandler: StreamedAsyncHandler[T] =>
        // Streamed requests don't go through the cache
        underlying.executeRequest(request, streamedHandler)

      case other =>
        throw new IllegalStateException(s"Unknown handler type ${other.getClass.getName}")
    }
  }

  @throws(classOf[IOException])
  protected def execute[T](request: Request, handler: AsyncCompletionHandler[T], future: ListenableFuture[_])(implicit ec: ExecutionContext): ListenableFuture[T] = {
    if (logger.isTraceEnabled) {
      logger.trace(s"execute: request = ${debug(request)}, handler = ${debug(handler)}, future = $future")
    }

    // Ask the cache if it has anything matching the primary key...
    val key = EffectiveURIKey(request)
    val requestTime = HttpDate.now
    val entryResults = Await.result(ahcHttpCache.get(key), cacheTimeout).toList
    if (logger.isDebugEnabled) {
      logger.debug(s"execute $key: results = $entryResults")
    }

    // Selects a response out of the results -- if there is no selected response, then
    // depending on the Cache-Control header values, the response may be to timeout or forward.
    ahcHttpCache.selectionAction(request, entryResults) match {
      case SelectedResponse(_, index) =>
        val entry = entryResults(index)
        logger.debug(s"execute $key: selected from cache: $entry")
        serveResponse(handler, request, entry, requestTime)

      case GatewayTimeout(reason) =>
        logger.debug(s"execute $key: $reason -- timing out ")
        serveTimeout(request, handler)

      case ForwardToOrigin(reason) =>
        logger.debug(s"execute $key: $reason -- forwarding to origin server")
        underlying.executeRequest(request, cacheAsyncHandler(request, handler))
    }
  }

  /**
   * Serves a future containing the response, based on the cache behavior.
   */
  protected def serveResponse[T](handler: AsyncCompletionHandler[T], request: Request, entry: ResponseEntry, requestTime: DateTime)(implicit ec: ExecutionContext): ListenableFuture[T] = {

    val key = EffectiveURIKey(request)

    val currentAge = ahcHttpCache.calculateCurrentAge(request, entry, requestTime)

    ahcHttpCache.serveAction(request, entry, currentAge) match {
      case ServeFresh(reason) =>
        logger.debug(s"serveResponse $key: $reason -- serving fresh response")

        // Serve fresh responses from cache, without going to the origin server.
        val freshResponse = ahcHttpCache.generateCachedResponse(request, entry, currentAge, isFresh = true)
        executeFromCache(handler, request, freshResponse)

      case ServeStale(reason) =>
        logger.debug(s"serveResponse $key: $reason -- serving stale response found for $key")

        // Serve stale response from cache, without going to the origin sever.
        val staleResponse = ahcHttpCache.generateCachedResponse(request, entry, currentAge, isFresh = false)
        executeFromCache(handler, request, staleResponse)

      case ServeStaleAndValidate(reason) =>
        logger.debug(s"serveResponse $key: $reason - serving stale response and revalidating for $key")

        // XXX FIXME How does stale-while-revalidate interact with POST / unsafe methods?
        //        A cache MUST write through requests with methods that are unsafe
        //    (Section 4.2.1 of [RFC7231]) to the origin server; i.e., a cache is
        //    not allowed to generate a reply to such a request before having
        //    forwarded the request and having received a corresponding response.

        // Run a validation request in a future (which will update the cache later)...
        val response = entry.response
        val validationRequest = buildValidationRequest(request, response)
        underlying.executeRequest(validationRequest, backgroundAsyncHandler[AHCResponse](validationRequest))

        // ...AND return the response from cache.
        val staleResponse = ahcHttpCache.generateCachedResponse(request, entry, currentAge, isFresh = false)
        executeFromCache(handler, request, staleResponse)

      case action @ Validate(reason, staleIfError) =>
        logger.debug(s"serveResponse $key: $reason -- revalidate with staleIfError = $staleIfError")

        // Stale response requires talking to the origin server first.
        // The origin server can return a 304 Not Modified with no body,
        // so the stale response could still be used... we just need to check.
        val response = entry.response
        val validationRequest = buildValidationRequest(request, response)
        underlying.executeRequest(validationRequest, cacheAsyncHandler(validationRequest, handler, Some(action)))

      case action @ ValidateOrTimeout(reason) =>
        logger.debug(s"serveResponse: $reason -- must revalidate and timeout on disconnect")

        // Same as validate, but if the origin server cannot be reached, return a 504 gateway
        // timeout response instead of serving a stale response.
        val response = entry.response
        val validationRequest = buildValidationRequest(request, response)
        underlying.executeRequest(validationRequest, cacheAsyncHandler(request, handler, Some(action)))
    }
  }

  protected def executeFromCache[T](handler: AsyncHandler[T], request: Request, response: CacheableResponse)(implicit ec: ExecutionContext): CacheFuture[T] = {
    logger.trace(s"executeFromCache: handler = ${debug(handler)}, request = ${debug(request)}, response = ${debug(response)}")

    val cacheFuture = new CacheFuture[T](handler)
    ec.execute(new Runnable {
      override def run(): Unit = new AsyncCacheableConnection[T](handler, request, response, cacheFuture).call()
    })
    cacheFuture
  }

  protected def buildValidationRequest(request: Request, response: CacheableResponse): Request = {
    logger.trace(s"buildValidationRequest: ${debug(request)}, response = ${debug(response)}")
    // https://tools.ietf.org/html/rfc7234#section-4.3.1
    // https://tools.ietf.org/html/rfc7232#section-2.4

    //A client:
    //
    //o  MUST send that entity-tag in any cache validation request (using
    //  If-Match or If-None-Match) if an entity-tag has been provided by
    //the origin server.
    //
    //o  SHOULD send the Last-Modified value in non-subrange cache
    //validation requests (using If-Modified-Since) if only a
    //Last-Modified value has been provided by the origin server.
    //
    //o  MAY send the Last-Modified value in subrange cache validation
    //requests (using If-Unmodified-Since) if only a Last-Modified value
    //has been provided by an HTTP/1.0 origin server.  The user agent
    //SHOULD provide a way to disable this, in case of difficulty.
    //
    //o  SHOULD send both validators in cache validation requests if both
    //an entity-tag and a Last-Modified value have been provided by the
    //origin server.  This allows both HTTP/1.0 and HTTP/1.1 caches to
    //respond appropriately.

    composeRequest(request) { rb =>
      val headers = response.getHeaders

      // https://tools.ietf.org/html/rfc7232#section-2.2
      Option(headers.get("Last-Modified")).map { lastModifiedDate =>
        rb.addHeader("If-Modified-Since", lastModifiedDate)
      }

      Option(headers.get("ETag")).map { eTag =>
        rb.addHeader("If-None-Match", eTag)
      }

      rb
    }
  }

  protected def composeRequest(request: Request)(block: RequestBuilder => RequestBuilder): Request = {
    val rb = new RequestBuilder(request)
    val builder = block(rb)
    builder.build()
  }

  protected def backgroundAsyncHandler[T](request: Request): BackgroundAsyncHandler[T] = {
    new BackgroundAsyncHandler(request, ahcHttpCache, underlying.getConfig)
  }

  protected def serveTimeout[T](request: Request, handler: AsyncHandler[T])(implicit ec: ExecutionContext): CacheFuture[T] = {
    val timeoutResponse = generateTimeoutResponse(request, underlying.getConfig)
    executeFromCache(handler, request, timeoutResponse)
  }

  protected def cacheAsyncHandler[T](request: Request, handler: AsyncCompletionHandler[T], action: Option[ResponseServeAction] = None): AsyncCachingHandler[T] = {
    new AsyncCachingHandler(request, handler, ahcHttpCache, action, underlying.getConfig)
  }

  override def prepareGet(s: String): BoundRequestBuilder = {
    underlying.prepareGet(s)
  }

  override def preparePost(s: String): BoundRequestBuilder = {
    underlying.preparePost(s)
  }

  override def preparePut(s: String): BoundRequestBuilder = {
    underlying.preparePut(s)
  }

  override def prepareOptions(s: String): BoundRequestBuilder = {
    underlying.prepareOptions(s)
  }

  override def setSignatureCalculator(signatureCalculator: SignatureCalculator): AsyncHttpClient = {
    underlying.setSignatureCalculator(signatureCalculator)
  }

  override def prepareHead(s: String): BoundRequestBuilder = {
    underlying.prepareHead(s)
  }

  override def prepareConnect(s: String): BoundRequestBuilder = {
    underlying.prepareConnect(s)
  }

  override def prepareTrace(s: String): BoundRequestBuilder = {
    underlying.prepareTrace(s)
  }

  override def prepareRequest(request: Request): BoundRequestBuilder = {
    underlying.prepareRequest(request)
  }

  override def prepareRequest(requestBuilder: RequestBuilder): BoundRequestBuilder = {
    underlying.prepareRequest(requestBuilder)
  }

  override def prepareDelete(s: String): BoundRequestBuilder = {
    underlying.prepareDelete(s)
  }

  override def preparePatch(s: String): BoundRequestBuilder = {
    underlying.preparePatch(s)
  }

  override def isClosed: Boolean = {
    underlying.isClosed
  }

  override def executeRequest[T](requestBuilder: RequestBuilder, asyncHandler: AsyncHandler[T]): ListenableFuture[T] = {
    executeRequest(requestBuilder.build(), asyncHandler)
  }

  override def executeRequest(request: Request): ListenableFuture[AHCResponse] = {
    underlying.executeRequest(request)
  }

  override def executeRequest(requestBuilder: RequestBuilder): ListenableFuture[AHCResponse] = {
    underlying.executeRequest(requestBuilder)
  }

  override def prepare(method: String, url: String): BoundRequestBuilder = underlying.prepare(method, url)

  override def getClientStats: ClientStats = underlying.getClientStats

  override def flushChannelPoolPartitions(predicate: Predicate[AnyRef]): Unit = underlying.flushChannelPoolPartitions(predicate)

  override def getConfig: AsyncHttpClientConfig = underlying.getConfig
}

