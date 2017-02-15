/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import javax.cache.Cache
import javax.inject.Inject

import akka.stream.Materializer
import com.typesafe.sslconfig.ssl.SystemConfiguration
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration
import play.api.libs.ws.ahc.cache._
import play.api.libs.ws.{ EmptyBody, StandaloneWSClient, StandaloneWSRequest, StreamedResponse }
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse, _ }

import scala.collection.immutable.TreeMap
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
 * A WS client backed by an AsyncHttpClient.
 *
 * If you need to debug AsyncHttpClient, add <logger name="play.shaded.ahc.org.asynchttpclient" level="DEBUG" /> into your conf/logback.xml file.
 *
 * @param asyncHttpClient An already configured asynchttpclient.
 *                        Note that the WSClient assumes ownership of the lifecycle here, so closing the WSClient will
 *                        also close asyncHttpClient.
 * @param materializer    An akka materializer.
 */
class StandaloneAhcWSClient @Inject() (asyncHttpClient: AsyncHttpClient)(implicit val materializer: Materializer) extends StandaloneWSClient {

  /** Returns instance of AsyncHttpClient */
  def underlying[T]: T = asyncHttpClient.asInstanceOf[T]

  def close(): Unit = {
    asyncHttpClient.close()
  }

  def url(url: String): StandaloneWSRequest = {
    validate(url)
    StandaloneAhcWSRequest(this, url, "GET", EmptyBody, TreeMap()(CaseInsensitiveOrdered), Map(), None, None, None, None, None, None, None)
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
    Streamed.execute(asyncHttpClient, request)(executionContext)
  }

  def executionContext: ExecutionContext = materializer.executionContext

}

object StandaloneAhcWSClient {

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
   * @param cache if not null, will be used for HTTP response caching.
   * @param materializer the akka materializer.
   */
  def apply(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig(), cache: Option[Cache[EffectiveURIKey, ResponseEntry]] = None)(implicit materializer: Materializer): StandaloneAhcWSClient = {
    if (config.wsClientConfig.ssl.debug.enabled) {
      new DebugConfiguration(StandaloneAhcWSClient.loggerFactory).configure(config.wsClientConfig.ssl.debug)
    }
    val ahcConfig = new AhcConfigBuilder(config).build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    val wsClient = new StandaloneAhcWSClient(
      cache.map {
        new CachingAsyncHttpClient(asyncHttpClient, _)
      }.getOrElse {
        asyncHttpClient
      }
    )
    new SystemConfiguration(loggerFactory).configure(config.wsClientConfig.ssl)
    wsClient
  }
}
