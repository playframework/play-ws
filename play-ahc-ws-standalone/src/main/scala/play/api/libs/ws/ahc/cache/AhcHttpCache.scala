/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.net.URI
import java.time.ZonedDateTime

import com.typesafe.play.cachecontrol._
import play.api.libs.ws.{ ahc => standaloneAhc }
import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Central HTTP cache.  This keeps a cache of HTTP responses according to
 * https://tools.ietf.org/html/rfc7234#section-2
 *
 * The primary cache key consists of the request method and target URI.
 * However, since HTTP caches in common use today are typically limited
 * to caching responses to GET, many caches simply decline other methods
 * and use only the URI as the primary cache key.
 */
class AhcHttpCache(underlying: standaloneAhc.cache.Cache, heuristicsEnabled: Boolean = false)(implicit
    val executionContext: ExecutionContext
) extends CacheDefaults
    with Debug {
  require(underlying != null, "null underlying!")

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val responseCachingCalculator = new ResponseCachingCalculator(this)

  private val responseServingCalculator = new ResponseServingCalculator(this)

  private val responseSelectionCalculator = new ResponseSelectionCalculator(this)

  private val stripHeaderCalculator = new StripHeaderCalculator(this)

  private val secondaryKeyCalculator = new SecondaryKeyCalculator()

  private val currentAgeCalculator = new CurrentAgeCalculator()

  private val freshnessCalculator = new FreshnessCalculator(this)

  /**
   * Cache is not shared.
   */
  override def isShared: Boolean = false

  def get(key: EffectiveURIKey): Future[Option[ResponseEntry]] = {
    logger.debug(s"get: key = $key")
    require(key != null, "key is null")
    underlying.get(key)
  }

  def put(key: EffectiveURIKey, entry: ResponseEntry): Future[Unit] = {
    logger.debug(s"put: key = $key, entry = $entry")
    require(entry != null, "value is null")
    underlying.put(key, entry)
  }

  def remove(key: EffectiveURIKey): Future[Unit] = {
    require(key != null, "key is null")
    underlying.remove(key)
  }

  /**
   * Invalidates the key.
   */
  def invalidateKey(key: EffectiveURIKey): Unit = {
    // mark any caches as stale by replacing the date with TSE
    underlying.get(key).map { maybeEntry =>
      maybeEntry.foreach { entry =>
        val expiredEntry = entry.copy(expiresAt = Some(HttpDate.fromEpochSeconds(0)))
        put(key, expiredEntry)
      }
    }
  }

  def cachingAction(request: Request, response: CacheableResponse): ResponseCachingAction = {
    val headers                        = response.headers
    val statusCode                     = response.getStatusCode
    val cacheRequest                   = generateCacheRequest(request)
    val originResponse: OriginResponse = generateOriginResponse(request, statusCode, headers)
    val action                         = responseCachingCalculator.isCacheable(cacheRequest, originResponse)
    action
  }

  def selectionAction(request: Request, entries: Seq[ResponseEntry]): ResponseSelectionAction = {
    val cacheRequest = generateCacheRequest(request)
    val storedResponses = entries.map { entry =>
      generateStoredResponse(entry.response, entry.requestMethod, entry.nominatedHeaders)
    }

    responseSelectionCalculator.selectResponse(cacheRequest, storedResponses)
  }

  def serveAction(request: Request, entry: ResponseEntry, currentAge: Seconds): ResponseServeAction = {
    val cacheRequest   = generateCacheRequest(request)
    val storedResponse = generateStoredResponse(entry.response, entry.requestMethod, entry.nominatedHeaders)

    responseServingCalculator.serveResponse(cacheRequest, storedResponse, currentAge)
  }

  override def calculateFreshnessFromHeuristic(request: CacheRequest, response: CacheResponse): Option[Seconds] = {
    if (heuristicsEnabled) {
      // https://publicobject.com/2015/03/26/how-do-http-caching-heuristics-work/
      response.headers.get(HeaderName("Last-Modified")).map { lastModifiedString =>
        val lastModified                   = HttpDate.parse(lastModifiedString.head)
        val lastRequestedAt                = HttpDate.now
        val timeSinceLastModified: Seconds = HttpDate.diff(start = lastModified, end = lastRequestedAt)
        // 10% of the duration
        val scaledDownSeconds      = (0.1 * timeSinceLastModified.seconds).toInt
        val scaledSeconds: Seconds = Seconds.seconds(scaledDownSeconds)
        scaledSeconds
      }
    } else {
      None
    }
  }

  override def isCacheableExtension(extension: CacheDirectives.CacheDirectiveExtension): Boolean = {
    false
  }

  def isNotModified(response: CacheableResponse): Boolean = {
    response.getStatusCode == 304
  }

  def isError(response: CacheableResponse): Boolean = {
    // In this context, an error is any situation that would result in a
    // 500, 502, 503, or 504 HTTP response status code being returned.
    // https://tools.ietf.org/html/rfc5861#section-3

    response.getStatusCode match {
      case 500 | 502 | 503 | 504 =>
        true
      case other =>
        false
    }
  }

  def isUnsafeMethod(request: Request): Boolean = {
    // Of the request methods defined by this specification, the GET, HEAD,
    // OPTIONS, and TRACE methods are defined to be safe.
    // https://tools.ietf.org/html/rfc7231#section-4.2.1
    request.getMethod match {
      case "GET" | "HEAD" | "OPTIONS" | "TRACE" =>
        false
      case other =>
        true
    }
  }

  /**
   * Calculates the current age of the stored response.
   */
  def calculateCurrentAge(request: Request, entry: ResponseEntry, requestTime: ZonedDateTime): Seconds = {
    val cacheRequest: CacheRequest = generateCacheRequest(request)
    val storedResponse: StoredResponse =
      generateStoredResponse(entry.response, entry.requestMethod, entry.nominatedHeaders)
    val currentAge = calculateCurrentAge(cacheRequest, storedResponse, requestTime, responseTime = HttpDate.now)
    currentAge
  }

  /**
   */
  def calculateFreshnessLifetime(request: Request, entry: ResponseEntry): Seconds = {
    val cacheRequest: CacheRequest = generateCacheRequest(request)
    val storedResponse: StoredResponse =
      generateStoredResponse(entry.response, entry.requestMethod, entry.nominatedHeaders)
    val freshnessLifetime = freshnessCalculator.calculateFreshnessLifetime(cacheRequest, storedResponse)
    freshnessLifetime
  }

  /**
   * Invalidates the effective request URI if the method is unsafe.
   */
  def invalidateIfUnsafe(request: Request, response: CacheableResponse): Unit = {
    logger.trace(s"invalidate: request = ${debug(request)}, response = ${debug(response)}")

    if (isUnsafeMethod(request) && isNonErrorResponse(response)) {
      val requestHost = request.getUri.getHost

      //A cache MUST invalidate the effective request URI (Section 5.5 of
      //[RFC7230]) when it receives a non-error response to a request with a
      //method whose safety is unknown.
      val responseKey = EffectiveURIKey(request.getMethod, response.getUri.toJavaNetURI)
      invalidateKey(responseKey)

      //A cache MUST invalidate the effective Request URI (Section 5.5 of
      //[RFC7230]) as well as the URI(s) in the Location and Content-Location
      //response header fields (if present) when a non-error status code is
      //received in response to an unsafe request method.

      // https://tools.ietf.org/html/rfc7231#section-3.1.4.2
      // https://tools.ietf.org/html/rfc7230#section-5.5
      getURI(response, "Content-Location").foreach { contentLocation =>
        //However, a cache MUST NOT invalidate a URI from a Location or
        //Content-Location response header field if the host part of that URI
        //differs from the host part in the effective request URI (Section 5.5
        //of [RFC7230]).  This helps prevent denial-of-service attacks.
        if (requestHost.equalsIgnoreCase(contentLocation.getHost)) {
          val key = EffectiveURIKey(request.getMethod, contentLocation)
          invalidateKey(key)
        }
      }

      getURI(response, "Location").foreach { location =>
        if (requestHost.equalsIgnoreCase(location.getHost)) {
          val key = EffectiveURIKey(request.getMethod, location)
          invalidateKey(key)
        }
      }
    }
  }

  /**
   * Gets the effective URI of the response.
   */
  protected def getURI(response: CacheableResponse, headerName: String): Option[URI] = {
    Option(response.getHeaders.get(headerName)).map { value =>
      // Gets the base URI, i.e. http://example.com/ so we can resolve relative URIs
      val baseURI = response.getUri.toJavaNetURI
      // So both absolute & relative URI will be resolved with example.com as base...
      baseURI.resolve(value)
    }
  }

  protected def isNonErrorResponse(response: CacheableResponse): Boolean = {
    //Here, a "non-error response" is one with a 2xx (Successful) or 3xx
    //(Redirection) status code.
    response.getStatusCode match {
      case success if success >= 200 && success < 300 =>
        true
      case redirect if redirect >= 300 && redirect < 400 =>
        true
      case other =>
        false
    }
  }

  /**
   * Calculates the secondary keys of the request.
   */
  def calculateSecondaryKeys(request: Request, response: Response): Option[Map[HeaderName, Seq[String]]] = {
    val cacheRequest = generateCacheRequest(request)
    val headers = headersToMap(response.getHeaders).map { case (name, values) =>
      (HeaderName(name), values)
    }

    secondaryKeyCalculator.calculate(cacheRequest, headers)
  }

  /**
   * Calculates the current age of the stored response.
   */
  protected def calculateCurrentAge(
      request: CacheRequest,
      response: StoredResponse,
      requestTime: ZonedDateTime,
      responseTime: ZonedDateTime
  ): Seconds = {
    currentAgeCalculator.calculateCurrentAge(request, response, requestTime, responseTime)
  }

  /**
   * Calculates the time to live.  Currently hardcoded to 24 hours.
   */
  protected def calculateTimeToLive(
      request: Request,
      status: CacheableHttpResponseStatus,
      headers: HttpHeaders
  ): Option[ZonedDateTime] = {
    Some(ZonedDateTime.now.plusHours(24))
  }

  /**
   * Caches the response, stripping any headers marked as "not-cacheable".
   */
  def cacheResponse(request: Request, response: CacheableResponse): Unit = {
    logger.debug(s"cacheResponse: response = ${debug(response)}")

    val strippedResponse = stripHeaders(request, response)
    logger.debug(s"cacheResponse: strippedResponse = ${debug(strippedResponse)}")

    val nominated = calculateSecondaryKeys(request, strippedResponse).getOrElse(Map())
    val ttl       = calculateTimeToLive(request, strippedResponse.status, strippedResponse.headers)
    val entry     = ResponseEntry(strippedResponse, request.getMethod, nominated, ttl)
    put(EffectiveURIKey(request), entry)
  }

  /**
   */
  def isUncachedResponse(any: Any): Boolean = {
    any match {
      case chrs: CacheableHttpResponseStatus =>
        false
      case headers: HttpHeaders =>
        false
      case bodyPart: CacheableHttpResponseBodyPart =>
        false
      case response: CacheableResponse =>
        false
      case _ =>
        true
    }
  }

  /**
   */
  def freshenResponse(newHeaders: HttpHeaders, response: CacheableResponse): CacheableResponse = {
    if (logger.isTraceEnabled) {
      logger.trace(s"freshenResponse: newHeaders = $newHeaders, storedResponse = $response")
    }

    import collection.JavaConverters._

    // Need to freshen this stale response
    // https://tools.ietf.org/html/rfc7234#section-4.3.4
    //If a stored response is selected for update, the cache MUST:
    //o  delete any Warning header fields in the stored response with
    //warn-code 1xx (see Section 5.5);
    //
    //o  retain any Warning header fields in the stored response with
    //warn-code 2xx; and,
    val headers                 = response.headers
    val headersMap: HttpHeaders = new DefaultHttpHeaders().add(headers)
    val filteredWarnings = headersMap
      .getAll("Warning")
      .asScala
      .filter { line =>
        val warning = WarningParser.parse(line)
        warning.code < 200
      }
      .asJava
    headersMap.set("Warning", filteredWarnings)

    //o  use other header fields provided in the 304 (Not Modified)
    //response to replace all instances of the corresponding header
    //fields in the stored response.
    headersMap.set(newHeaders)

    response.copy(headers = headersMap)
  }

  /**
   * Generates a response for the HTTP response with the appropriate headers.
   */
  def generateCachedResponse(
      request: Request,
      entry: ResponseEntry,
      currentAge: Seconds,
      isFresh: Boolean
  ): CacheableResponse = {
    replaceHeaders(entry.response) { headers =>
      //    When a stored response is used to satisfy a request without
      //    validation, a cache MUST generate an Age header field (Section 5.1),
      //    replacing any present in the response with a value equal to the
      //    stored response's current_age; see Section 4.2.3.
      headers.set("Age", currentAge.seconds.toString)
      if (!isFresh) {
        //    A cache SHOULD generate a Warning header field with the 110 warn-code
        //    (see Section 5.5.1) in stale responses.  Likewise, a cache SHOULD
        //    generate a 112 warn-code (see Section 5.5.3) in stale responses if
        //      the cache is disconnected.
        //    A cache SHOULD NOT generate a new Warning header field when
        //      forwarding a response that does not have an Age header field, even if
        //      the response is already stale.  A cache need not validate a response
        //    that merely became stale in transit.
        headers.add("Warning", Warning(110, "-", "Response is Stale", None).toString())
      }
      headers
    }
  }

  def addRevalidationFailed(response: CacheableResponse): CacheableResponse = {
    replaceHeaders(response) { headers =>
      headers.add("Warning", Warning(111, "-", "Revalidation Failed", None).toString())
    }
  }

  def addDisconnectHeader(response: CacheableResponse): CacheableResponse = {
    replaceHeaders(response) { headers =>
      headers.add("Warning", Warning(112, "-", "Disconnected Operation", None).toString())
    }
  }

  def replaceHeaders(response: CacheableResponse)(block: HttpHeaders => HttpHeaders): CacheableResponse = {
    val newHeadersMap = block(new DefaultHttpHeaders().add(response.getHeaders))
    response.copy(headers = newHeadersMap)
  }

  protected def generateCacheRequest(request: Request): CacheRequest = {
    val uri = request.getUri.toJavaNetURI
    val headers = headersToMap(request.getHeaders).map { case (name, values) =>
      (HeaderName(name), values)
    }
    val method = request.getMethod
    CacheRequest(uri = uri, method = method, headers = headers)
  }

  protected def generateStoredResponse(
      response: CacheableResponse,
      requestMethod: String,
      nominatedHeaders: Map[HeaderName, Seq[String]]
  ): StoredResponse = {
    val uri: URI        = response.getUri.toJavaNetURI
    val status: Int     = response.getStatusCode
    val responseHeaders = response.getHeaders
    val headers = headersToMap(responseHeaders).map { case (name, values) =>
      (HeaderName(name), values)
    }

    StoredResponse(
      uri = uri,
      status = status,
      headers = headers,
      requestMethod = requestMethod,
      nominatedHeaders = nominatedHeaders
    )
  }

  protected def generateOriginResponse(request: Request, status: Int, responseHeaders: HttpHeaders): OriginResponse = {
    val uri = request.getUri.toJavaNetURI
    val headers = headersToMap(responseHeaders).map { case (name, values) =>
      (HeaderName(name), values)
    }
    OriginResponse(uri, status, headers)
  }

  /**
   * Strips headers using a strip headers cache-control calculator.
   */
  protected def stripHeaders(request: Request, httpResponse: CacheableResponse): CacheableResponse = {
    val originResponse = generateOriginResponse(request, httpResponse.getStatusCode, httpResponse.headers)
    val stripSet       = stripHeaderCalculator.stripHeaders(originResponse)

    val r = if (stripSet.nonEmpty) {
      import scala.collection.JavaConverters._
      val stripHeaderNames = stripSet.map(_.toString()).asJavaCollection
      logger.debug(s"massageCachedResponse: stripHeaderNames = $stripHeaderNames")
      stripHeaderNames.asScala.foreach(httpResponse.getHeaders.remove)
      logger.debug(s"massageCachedResponse: strippedHeaders = ${httpResponse.getHeaders}")
      httpResponse.copy(headers = httpResponse.getHeaders)
    } else {
      httpResponse
    }
    r
  }

  def close(): Unit = {
    underlying.close()
  }

  override def toString: String = {
    s"AhcHttpCache(${underlying})"
  }
}
