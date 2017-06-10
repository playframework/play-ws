/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import javax.inject.Inject

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.sslconfig.ssl.SystemConfiguration
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration
import play.api.libs.ws.ahc.cache._
import play.api.libs.ws.{ EmptyBody, StandaloneWSClient, StandaloneWSRequest }
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse, _ }

import scala.collection.immutable.TreeMap
import scala.compat.java8.FunctionConverters
import scala.concurrent.{ Await, Future, Promise }
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
class StandaloneAhcWSClient @Inject() (asyncHttpClient: AsyncHttpClient)(implicit materializer: Materializer) extends StandaloneWSClient {

  /** Returns instance of AsyncHttpClient */
  def underlying[T]: T = asyncHttpClient.asInstanceOf[T]

  def close(): Unit = {
    asyncHttpClient.close()
  }

  def url(url: String): StandaloneWSRequest = {
    validate(url)
    StandaloneAhcWSRequest(
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
  }

  private[ahc] def execute(request: Request): Future[StandaloneAhcWSResponse] = {
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

  private def validate(url: String): Unit = {
    // Recover from https://github.com/AsyncHttpClient/async-http-client/issues/1149
    Try(Uri.create(url)).transform(Success(_), {
      case npe: NullPointerException =>
        Failure(new IllegalArgumentException(s"Invalid URL $url", npe))
    }).get
  }

  private[ahc] def executeStream(request: Request): Future[StreamedResponse] = {
    val promise = Promise[StreamedResponse]()

    val function = FunctionConverters.asJavaFunction[StreamedState, StreamedResponse](state =>
      new StreamedResponse(
        this,
        state.statusCode,
        state.statusText,
        state.uriOption.get,
        state.responseHeaders,
        state.publisher)
    )
    asyncHttpClient.executeRequest(request, new DefaultStreamedAsyncHandler[StreamedResponse](function, promise))
    promise.future
  }

  private[ahc] def blockingToByteString(bodyAsSource: Source[ByteString, _]) = {
    StandaloneAhcWSClient.logger.warn(s"blockingToByteString is a blocking and unsafe operation!")

    import scala.concurrent.ExecutionContext.Implicits.global

    val limitedSource = bodyAsSource.limit(StandaloneAhcWSClient.elementLimit)
    val result = limitedSource.runFold(ByteString.createBuilder) { (acc, bs) =>
      acc.append(bs)
    }.map(_.result())

    Await.result(result, StandaloneAhcWSClient.blockingTimeout)
  }

}

object StandaloneAhcWSClient {

  import scala.concurrent.duration._
  val blockingTimeout = 50.milliseconds
  val elementLimit = 13 // 13 8192k blocks is roughly 100k
  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private[ahc] val loggerFactory = new AhcLoggerFactory(org.slf4j.LoggerFactory.getILoggerFactory)

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
  def apply(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig(), httpCache: Option[AhcHttpCache] = None)(implicit materializer: Materializer): StandaloneAhcWSClient = {
    if (config.wsClientConfig.ssl.debug.enabled) {
      new DebugConfiguration(StandaloneAhcWSClient.loggerFactory).configure(config.wsClientConfig.ssl.debug)
    }
    val ahcConfig = new AhcConfigBuilder(config).build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    val wsClient = new StandaloneAhcWSClient(
      httpCache.map { cache =>
        new CachingAsyncHttpClient(asyncHttpClient, cache)
      }.getOrElse {
        asyncHttpClient
      }
    )
    new SystemConfiguration(loggerFactory).configure(config.wsClientConfig.ssl)
    wsClient
  }
}

