/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

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

import scala.collection.immutable.TreeMap
import scala.jdk.FunctionConverters._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

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
class StandaloneAhcWSClient @Inject() (asyncHttpClient: AsyncHttpClient)(implicit
    materializer: Materializer
) extends StandaloneWSClient {

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

  private def validate(url: String): Unit = {
    // Recover from https://github.com/AsyncHttpClient/async-http-client/issues/1149
    try {
      Uri.create(url)
    } catch {
      case iae: IllegalArgumentException =>
        throw new IllegalArgumentException(s"Invalid URL $url", iae)
      case npe: NullPointerException =>
        throw new IllegalArgumentException(s"Invalid URL $url", npe)
    }
  }

  private[ahc] def executeStream(request: Request): Future[StreamedResponse] = {
    val streamStarted    = Promise[StreamedResponse]()
    val streamCompletion = Promise[Done]()

    val client = this

    val function: JFunction[StreamedState, StreamedResponse] = { (state: StreamedState) =>
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
  val elementLimit    = 13 // 13 8192k blocks is roughly 100k
  private val logger  = org.slf4j.LoggerFactory.getLogger(this.getClass)

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
   * import play.api.libs.ws.ahc.StandaloneAhcWSClient
   *
   * def example(someUrl: String)(implicit m: akka.stream.Materializer) = {
   *   implicit def ec = m.executionContext
   *
   *   val client = StandaloneAhcWSClient()
   *   val request = client.url(someUrl).get()
   *
   *   request.foreach { response =>
   *     //doSomething(response)
   *     client.close()
   *   }
   * }
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
    val ahcConfig       = new AhcConfigBuilder(config).build()
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
}
