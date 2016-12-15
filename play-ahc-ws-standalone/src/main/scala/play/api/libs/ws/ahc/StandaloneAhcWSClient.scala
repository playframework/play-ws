package play.api.libs.ws.ahc

import akka.stream.Materializer
import com.typesafe.sslconfig.ssl.SystemConfiguration
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration
import org.asynchttpclient._
import play.api.libs.ws.{EmptyBody, StandaloneWSClient, StandaloneWSRequest}

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext


/**
 * A WS client backed by an AsyncHttpClient.
 *
 * If you need to debug AsyncHttpClient, add <logger name="org.asynchttpclient" level="DEBUG" /> into your conf/logback.xml file.
 *
 * @param asyncHttpClient an already configured asynchttpclient
 */
case class StandaloneAhcWSClient(asyncHttpClient: AsyncHttpClient)(implicit val materializer: Materializer) extends StandaloneWSClient {

  def underlying[T]: T = asyncHttpClient.asInstanceOf[T]

  private[libs] def executionContext: ExecutionContext = materializer.executionContext

  private[libs] def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = asyncHttpClient.executeRequest(request, handler)

  def close(): Unit = asyncHttpClient.close()

  def url(url: String): StandaloneWSRequest = StandaloneAhcWSRequest(this, url, "GET", EmptyBody, TreeMap()(CaseInsensitiveOrdered), Map(), None, None, None, None, None, None, None)
}

object StandaloneAhcWSClient {

  private[ahc] val loggerFactory = new AhcLoggerFactory

  /**
   * Convenient factory method that uses a [[play.api.libs.ws.WSClientConfig]] value for configuration instead of
   * an [[http://static.javadoc.io/org.asynchttpclient/async-http-client/2.0.0/org/asynchttpclient/AsyncHttpClientConfig.html org.asynchttpclient.AsyncHttpClientConfig]].
   *
   * Typical usage:
   *
   * {{{
   *   val client = AhcWSClient()
   *   val request = client.url(someUrl).get()
   *   request.foreach { response =>
   *     doSomething(response)
   *     client.close()
   *   }
   * }}}
   *
   * @param config configuration settings
   */
  def apply(config: AhcWSClientConfig = AhcWSClientConfig())(implicit materializer: Materializer): StandaloneAhcWSClient = {
    if (config.wsClientConfig.ssl.debug.enabled) {
      new DebugConfiguration(StandaloneAhcWSClient.loggerFactory).configure(config.wsClientConfig.ssl.debug)
    }
    val ahcConfig = new AhcConfigBuilder(config).build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    val client = new StandaloneAhcWSClient(asyncHttpClient) // add the wrapper around AHC
    new SystemConfiguration(loggerFactory).configure(config.wsClientConfig.ssl)
    client
  }
}
