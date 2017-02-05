/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import javax.cache.Cache

import akka.stream.Materializer
import com.typesafe.sslconfig.ssl.SystemConfiguration
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration
import play.api.libs.ws.ahc.cache._
import play.api.libs.ws.{EmptyBody, StandaloneWSClient, StandaloneWSRequest}
import play.shaded.ahc.org.asynchttpclient._

import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * A WS client backed by an AsyncHttpClient.
 *
 * If you need to debug AsyncHttpClient, add <logger name="play.shaded.ahc.org.asynchttpclient" level="DEBUG" /> into your conf/logback.xml file.
 *
 * @param asyncHttpClient an already configured asynchttpclient
 * @param cache An optional HTTP caching layer.
 */
class StandaloneAhcWSClient(asyncHttpClient: AsyncHttpClient, cache: Option[Cache[EffectiveURIKey, ResponseEntry]] = None)(implicit val materializer: Materializer) extends StandaloneWSClient {

  private val httpClient: AsyncHttpClient = {
    cache.map(AhcHttpCache(_)).map {
      new CachingAsyncHttpClient(asyncHttpClient, _)
    }.getOrElse(asyncHttpClient)
  }

  def underlying[T]: T = asyncHttpClient.asInstanceOf[T]

  private[libs] def executionContext: ExecutionContext = materializer.executionContext

  private[libs] def execute(request: Request): Future[StandaloneAhcWSResponse] = {
    import play.shaded.ahc.org.asynchttpclient.{ AsyncCompletionHandler, Response => AHCResponse }
    import scala.concurrent.Promise

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

    httpClient.executeRequest(request, handler)
    result.future
  }

  private[libs] def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = {
    asyncHttpClient.executeRequest(request, handler)
  }

  def close(): Unit = {
    asyncHttpClient.close()
  }

  def url(url: String): StandaloneWSRequest = StandaloneAhcWSRequest(this, url, "GET", EmptyBody, TreeMap()(CaseInsensitiveOrdered), Map(), None, None, None, None, None, None, None)
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
   */
  def apply(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig(), cache: Option[Cache[EffectiveURIKey, ResponseEntry]] = None)(implicit materializer: Materializer): StandaloneAhcWSClient = {
    if (config.wsClientConfig.ssl.debug.enabled) {
      new DebugConfiguration(StandaloneAhcWSClient.loggerFactory).configure(config.wsClientConfig.ssl.debug)
    }
    val ahcConfig = new AhcConfigBuilder(config).build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    val client = new StandaloneAhcWSClient(asyncHttpClient, cache)
    new SystemConfiguration(loggerFactory).configure(config.wsClientConfig.ssl)
    client
  }
}
