/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package play.api.libs.ws.ahc.cache

import java.io._
import java.util.concurrent.Executors

import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse, _ }

import scala.concurrent.Await

trait TimeoutResponse {

  def generateTimeoutResponse(request: Request): CacheableResponse = {
    val uri = request.getUri
    val status = new CacheableHttpResponseStatus(uri, 504, "Gateway Timeout", "")
    val headers = new CacheableHttpResponseHeaders(false, new DefaultHttpHeaders())
    val bodyParts = java.util.Collections.emptyList[CacheableHttpResponseBodyPart]()
    CacheableResponse(status, headers, bodyParts)
  }
}

/**
 * A provider that pulls a response from the cache.
 */
class CachingAsyncHttpClient(client: AsyncHttpClient, cache: AhcHttpCache)
    extends AsyncHttpClient
    with TimeoutResponse
    with Debug {

  private val cacheThreadPool = Executors.newFixedThreadPool(2)

  private val logger = LoggerFactory.getLogger(this.getClass)

  import com.typesafe.play.cachecontrol.ResponseSelectionActions._
  import com.typesafe.play.cachecontrol.ResponseServeActions._
  import com.typesafe.play.cachecontrol._

  private val cacheTimeout = scala.concurrent.duration.Duration(1, "second")

  def close(): Unit = {
    if (logger.isTraceEnabled) {
      logger.trace("close: ")
    }
    client.close()
  }

  @throws(classOf[IOException])
  override def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = {
    handler match {
      case asyncCompletionHandler: AsyncCompletionHandler[T] =>
        execute(request, asyncCompletionHandler, null)

      case other =>
        throw new IllegalStateException(s"Only AsyncCompletionHandler is implemented, type is ${other.getClass}" )
    }
  }

  @throws(classOf[IOException])
  protected def execute[T](request: Request, handler: AsyncCompletionHandler[T], future: ListenableFuture[_]): ListenableFuture[T] = {
    if (logger.isTraceEnabled) {
      logger.trace(s"execute: request = ${debug(request)}, handler = ${debug(handler)}, future = $future")
    }

    // Ask the cache if it has anything matching the primary key...
    val key = EffectiveURIKey(request)
    val requestTime = HttpDate.now
    val entryResults = Await.result(cache.get(key), cacheTimeout).toSeq
    if (logger.isDebugEnabled) {
      logger.debug(s"execute $key: results = $entryResults")
    }

    // Selects a response out of the results -- if there is no selected response, then
    // depending on the Cache-Control header values, the response may be to timeout or forward.
    cache.selectionAction(request, entryResults) match {
      case SelectedResponse(_, index) =>
        val entry = entryResults(index)
        logger.debug(s"execute $key: selected from cache: $entry")
        serveResponse(handler, request, entry, requestTime)

      case GatewayTimeout(reason) =>
        logger.debug(s"execute $key: $reason -- timing out ")
        serveTimeout(request, handler)

      case ForwardToOrigin(reason) =>
        logger.debug(s"execute $key: $reason -- forwarding to origin server")
        client.executeRequest(request, cacheAsyncHandler(request, handler))
    }
  }

  /**
   * Serves a future containing the response, based on the cache behavior.
   */
  protected def serveResponse[T](handler: AsyncCompletionHandler[T], request: Request, entry: ResponseEntry, requestTime: DateTime): ListenableFuture[T] = {

    val key = EffectiveURIKey(request)

    val currentAge = cache.calculateCurrentAge(request, entry, requestTime)

    cache.serveAction(request, entry, currentAge) match {
      case ServeFresh(reason) =>
        logger.debug(s"serveResponse $key: $reason -- serving fresh response")

        // Serve fresh responses from cache, without going to the origin server.
        val freshResponse = cache.generateCachedResponse(request, entry, currentAge, isFresh = true)
        executeFromCache(handler, request, freshResponse)

      case ServeStale(reason) =>
        logger.debug(s"serveResponse $key: $reason -- serving stale response found for $key")

        // Serve stale response from cache, without going to the origin sever.
        val staleResponse = cache.generateCachedResponse(request, entry, currentAge, isFresh = false)
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
        client.executeRequest(validationRequest, backgroundAsyncHandler[AHCResponse](validationRequest))

        // ...AND return the response from cache.
        val staleResponse = cache.generateCachedResponse(request, entry, currentAge, isFresh = false)
        executeFromCache(handler, request, staleResponse)

      case action @ Validate(reason, staleIfError) =>
        logger.debug(s"serveResponse $key: $reason -- revalidate with staleIfError = $staleIfError")

        // Stale response requires talking to the origin server first.
        // The origin server can return a 304 Not Modified with no body,
        // so the stale response could still be used... we just need to check.
        val response = entry.response
        val validationRequest = buildValidationRequest(request, response)
        client.executeRequest(validationRequest, cacheAsyncHandler(validationRequest, handler, Some(action)))

      case action @ ValidateOrTimeout(reason) =>
        logger.debug(s"serveResponse: $reason -- must revalidate and timeout on disconnect")

        // Same as validate, but if the origin server cannot be reached, return a 504 gateway
        // timeout response instead of serving a stale response.
        val response = entry.response
        val validationRequest = buildValidationRequest(request, response)
        client.executeRequest(validationRequest, cacheAsyncHandler(request, handler, Some(action)))
    }
  }

  protected def executeFromCache[T](handler: AsyncHandler[T], request: Request, response: CacheableResponse): CacheFuture[T] = {
    logger.trace(s"executeFromCache: handler = ${debug(handler)}, request = ${debug(request)}, response = ${debug(response)}")

    val cacheFuture = new CacheFuture[T](handler)
    val callable = new AsyncCacheableConnection[T](handler, request, response, cacheFuture)
    cacheThreadPool.submit(callable)
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
    new BackgroundAsyncHandler(request, cache)
  }

  protected def serveTimeout[T](request: Request, handler: AsyncHandler[T]): CacheFuture[T] = {
    val timeoutResponse = generateTimeoutResponse(request)
    executeFromCache(handler, request, timeoutResponse)
  }

  protected def cacheAsyncHandler[T](request: Request, handler: AsyncCompletionHandler[T], action: Option[ResponseServeAction] = None): AsyncCachingHandler[T] = {
    new AsyncCachingHandler(request, handler, cache, action)
  }

  override def prepareGet(s: String): BoundRequestBuilder = {
    client.prepareGet(s)
  }

  override def preparePost(s: String): BoundRequestBuilder = {
    client.preparePost(s)
  }

  override def preparePut(s: String): BoundRequestBuilder = {
    client.preparePut(s)
  }

  override def prepareOptions(s: String): BoundRequestBuilder = {
    client.prepareOptions(s)
  }

  override def setSignatureCalculator(signatureCalculator: SignatureCalculator): AsyncHttpClient = {
    client.setSignatureCalculator(signatureCalculator)
  }

  override def prepareHead(s: String): BoundRequestBuilder = {
    client.prepareHead(s)
  }

  override def prepareConnect(s: String): BoundRequestBuilder = {
    client.prepareConnect(s)
  }

  override def prepareTrace(s: String): BoundRequestBuilder = {
    client.prepareTrace(s)
  }

  override def prepareRequest(request: Request): BoundRequestBuilder = {
    client.prepareRequest(request)
  }

  override def prepareRequest(requestBuilder: RequestBuilder): BoundRequestBuilder = {
    client.prepareRequest(requestBuilder)
  }

  override def prepareDelete(s: String): BoundRequestBuilder = {
    client.prepareDelete(s)
  }

  override def preparePatch(s: String): BoundRequestBuilder = {
    client.preparePatch(s)
  }

  override def isClosed: Boolean = {
    client.isClosed
  }

  override def executeRequest[T](requestBuilder: RequestBuilder, asyncHandler: AsyncHandler[T]): ListenableFuture[T] = {
    executeRequest(requestBuilder.build(), asyncHandler)
  }

  override def executeRequest(request: Request): ListenableFuture[AHCResponse] = {
    client.executeRequest(request)
  }

  override def executeRequest(requestBuilder: RequestBuilder): ListenableFuture[AHCResponse] = {
    client.executeRequest(requestBuilder)
  }
}

