/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.net.URLDecoder
import java.util.Collections

import akka.Done
import javax.inject.Inject
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.sslconfig.ssl.SystemConfiguration
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import play.api.libs.ws.ahc.cache._
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.StandaloneWSRequest
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }
import play.shaded.ahc.org.asynchttpclient._
import java.util.function.{ Function => JFunction }

import play.shaded.ahc.org.asynchttpclient.util.UriEncoder

import scala.collection.immutable.TreeMap
import scala.compat.java8.FunctionConverters._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/**
 * A WS client backed by an AsyncHttpClient.
 *
 * If you need to debug AsyncHttpClient, add <logger name="play.shaded.ahc.org.asynchttpclient" level="DEBUG" /> into your conf/logback.xml file.
 *
 * @param asyncHttpClient An already configured asynchttpclient.
 *                        Note that the WSClient assumes ownership of the lifecycle here, so closing the WSClient will
 *                        also close asyncHttpClient.
 * @param materializer A materializer, meant to execute the stream
 */
class StandaloneAhcWSClient @Inject() (asyncHttpClient: AsyncHttpClient)(
    implicit
    materializer: Materializer
) extends StandaloneWSClient {

  /** Returns instance of AsyncHttpClient */
  def underlying[T]: T = asyncHttpClient.asInstanceOf[T]

  def close(): Unit = {
    asyncHttpClient.close()
  }

  def url(url: String): StandaloneWSRequest = {
    val req = StandaloneAhcWSRequest(
      client = this,
      url = url,
      method = "GET",
      body = EmptyBody,
      headers = TreeMap()(CaseInsensitiveOrdered),
      queryString = Map.empty,
      cookies = Seq.empty,
      calc = None,
      auth = None,
      followRedirects = None,
      requestTimeout = None,
      virtualHost = None,
      proxyServer = None,
      disableUrlEncoding = None
    )

    StandaloneAhcWSClient.normalize(req)
  }

  private[ahc] def execute(
    request: Request
  ): Future[StandaloneAhcWSResponse] = {
    val result = Promise[StandaloneAhcWSResponse]()
    val handler = new AsyncCompletionHandler[AHCResponse]() {
      override def onCompleted(response: AHCResponse): AHCResponse = {
        result.success(StandaloneAhcWSResponse(response))
        response
      }

      override def onThrowable(t: Throwable): Unit = {
        result.failure(t)
      }
    }

    asyncHttpClient.executeRequest(request, handler)
    result.future
  }

  private[ahc] def executeStream(request: Request): Future[StreamedResponse] = {
    val streamStarted = Promise[StreamedResponse]()
    val streamCompletion = Promise[Done]()

    val client = this

    val function: JFunction[StreamedState, StreamedResponse] = {
      state: StreamedState =>
        val publisher = state.publisher

        val wrap = new Publisher[HttpResponseBodyPart]() {
          override def subscribe(
            s: Subscriber[_ >: HttpResponseBodyPart]
          ): Unit = {
            publisher.subscribe(new Subscriber[HttpResponseBodyPart] {
              override def onSubscribe(sub: Subscription): Unit =
                s.onSubscribe(sub)

              override def onNext(t: HttpResponseBodyPart): Unit = s.onNext(t)

              override def onError(t: Throwable): Unit = s.onError(t)

              override def onComplete(): Unit = {
                streamCompletion.future.onComplete {
                  case Success(_) => s.onComplete()
                  case Failure(t) => s.onError(t)
                }(materializer.executionContext)
              }
            })
          }

        }
        new StreamedResponse(
          client,
          state.statusCode,
          state.statusText,
          state.uriOption.get,
          state.responseHeaders,
          wrap,
          asyncHttpClient.getConfig.isUseLaxCookieEncoder
        )

    }.asJava
    asyncHttpClient.executeRequest(
      request,
      new DefaultStreamedAsyncHandler[StreamedResponse](
        function,
        streamStarted,
        streamCompletion
      )
    )
    streamStarted.future
  }

  private[ahc] def blockingToByteString(bodyAsSource: Source[ByteString, _]) = {
    StandaloneAhcWSClient.logger.warn(
      s"blockingToByteString is a blocking and unsafe operation!"
    )

    import scala.concurrent.ExecutionContext.Implicits.global

    val limitedSource = bodyAsSource.limit(StandaloneAhcWSClient.elementLimit)
    val result = limitedSource
      .runFold(ByteString.createBuilder) { (acc, bs) =>
        acc.append(bs)
      }
      .map(_.result())

    Await.result(result, StandaloneAhcWSClient.blockingTimeout)
  }

}

object StandaloneAhcWSClient {

  import scala.concurrent.duration._
  val blockingTimeout = 50.milliseconds
  val elementLimit = 13 // 13 8192k blocks is roughly 100k
  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private[ahc] val loggerFactory = new AhcLoggerFactory(
    org.slf4j.LoggerFactory.getILoggerFactory
  )

  /**
   * Convenient factory method that uses a play.api.libs.ws.WSClientConfig value for configuration instead of
   * an [[http://static.javadoc.io/org.asynchttpclient/async-http-client/2.0.0/org/asynchttpclient/AsyncHttpClientConfig.html org.asynchttpclient.AsyncHttpClientConfig]].
   *
   * Typical usage:
   *
   * {{{
   *   val client = StandaloneAhcWSClient()
   *   val request = client.url(someUrl).get()
   *   request.foreach { response =>
   *     doSomething(response)
   *     client.close()
   *   }
   * }}}
   *
   * @param config configuration settings
   * @param httpCache if not null, will be used for HTTP response caching.
   * @param materializer the akka materializer.
   */
  def apply(
    config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig(),
    httpCache: Option[AhcHttpCache] = None
  )(implicit materializer: Materializer): StandaloneAhcWSClient = {
    val ahcConfig = new AhcConfigBuilder(config).build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    val wsClient = new StandaloneAhcWSClient(
      httpCache
        .map { cache =>
          new CachingAsyncHttpClient(asyncHttpClient, cache)
        }
        .getOrElse {
          asyncHttpClient
        }
    )
    new SystemConfiguration(loggerFactory).configure(config.wsClientConfig.ssl)
    wsClient
  }

  /**
   * Ensures:
   *
   * 1. [[StandaloneWSRequest.url]] path is encoded, e.g.
   *    ws.url("http://example.com/foo bar") ->
   *    ws.url("http://example.com/foo%20bar")
   *
   * 2. Any query params present in the URL are moved to [[StandaloneWSRequest.queryString]], e.g.
   *    ws.url("http://example.com/?foo=bar") ->
   *    ws.url("http://example.com/").withQueryString("foo" -> "bar")
   */
  @throws[IllegalArgumentException]("if the url is unrepairable")
  private[ahc] def normalize(req: StandaloneAhcWSRequest): StandaloneWSRequest = {
    import play.shaded.ahc.org.asynchttpclient.util.MiscUtils.isEmpty
    if (req.url.indexOf('?') != -1) {
      // Query params in the path. Move them to the queryParams: Map.
      repair(req)
    } else {
      Try(req.uri) match {
        case Success(uri) =>

          /*
           * [[Uri.create()]] throws if the host or scheme is missing.
           * We can do those checks against the the [[java.net.URI]]
           * to avoid incurring the cost of re-parsing the URL string.
           *
           * @see https://github.com/AsyncHttpClient/async-http-client/issues/1149
           */
          if (isEmpty(uri.getScheme)) {
            throw new IllegalArgumentException(req.url + " could not be parsed into a proper Uri, missing scheme")
          }
          if (isEmpty(uri.getHost)) {
            throw new IllegalArgumentException(req.url + " could not be parsed into a proper Uri, missing host")
          }

          req
        case Failure(_) =>
          // URI parsing error. Sometimes recoverable by UriEncoder.FIXING
          repair(req)
      }
    }
  }

  /**
   * Encodes the URI to [[Uri]] and runs it through the same [[UriEncoder.FIXING]]
   * that async-http-client uses before executing it.
   */
  @throws[IllegalArgumentException]("if the url is unrepairable")
  private def repair(req: StandaloneAhcWSRequest): StandaloneWSRequest = {
    try {
      val encodedAhcUri: Uri = toUri(req)
      val javaUri = encodedAhcUri.toJavaNetURI
      setUri(req, encodedAhcUri.withNewQuery(null).toUrl, Option(javaUri.getRawQuery))
    } catch {
      case NonFatal(t) =>
        throw new IllegalArgumentException(s"Invalid URL ${req.url}", t)
    }
  }

  /**
   * Builds an AHC [[Uri]] with all parts URL encoded by [[UriEncoder.FIXING]].
   * Combines query params from both [[StandaloneWSRequest.url]] and [[StandaloneWSRequest.queryString]].
   */
  private def toUri(req: StandaloneWSRequest): Uri = {
    val combinedUri: Uri = {
      val uri = Uri.create(req.url)

      val paramsMap = req.queryString
      if (paramsMap.nonEmpty) {
        val query: String = combineQuery(uri.getQuery, paramsMap)
        uri.withNewQuery(query)
      } else {
        uri
      }
    }

    // FIXING.encode() encodes ONLY unencoded parts, leaving encoded parts untouched.
    UriEncoder.FIXING.encode(combinedUri, Collections.emptyList())
  }

  private def combineQuery(query: String, params: Map[String, Seq[String]]): String = {
    val sb = new StringBuilder
    // Reminder: ahc.Uri.query does include '?' (unlike java.net.URI)
    if (query != null) {
      sb.append(query)
    }

    for {
      (key, values) <- params
      value <- values
    } {
      if (sb.nonEmpty) {
        sb.append('&')
      }
      sb.append(key)
      if (value.nonEmpty) {
        sb.append('=').append(value)
      }
    }

    sb.toString
  }

  /**
   * Replace the [[StandaloneWSRequest.url]] and [[StandaloneWSRequest.queryString]]
   * with the values of [[uri]], discarding originals.
   */
  private def setUri(
    req: StandaloneAhcWSRequest,
    urlNoQueryParams: String,
    encodedQueryString: Option[String]): StandaloneWSRequest = {
    val queryParams: List[(String, String)] = for {
      queryString <- encodedQueryString.toList
      // https://stackoverflow.com/a/13592567 for all of this.
      pair <- queryString.split('&')
      idx = pair.indexOf('=')
      key = URLDecoder.decode(if (idx > 0) pair.substring(0, idx) else pair, "UTF-8")
      value = if (idx > 0) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
    } yield key -> value

    req
      // Intentionally using copy(url) instead of withUrl(url) to avoid
      // withUrl() -> normalize() -> withUrl() -> normalize()
      // just in case we missed a case.
      .copy(url = urlNoQueryParams)(req.materializer)
      .withQueryStringParameters(queryParams: _*)
  }
}
