package play.api.libs.ws.ning.cache

import play.shaded.ahc.org.asynchttpclient._
import play.shaded.ahc.org.asynchttpclient.NettyAsyncHttpProvider
import play.api.libs.ws.ahc._
import play.api.libs.ws._

/**
 * A Ning WS Client with built in caching.
 */
class CachingNingWSClient(config: AsyncHttpClientConfig, c: NingWSCache) extends AhcWSClient(config) {

  private val asyncHttpClient: AsyncHttpClient = {
    val httpProvider = new NettyAsyncHttpProvider(config)
    val cacheProvider = new CacheAsyncHttpProvider(config, httpProvider, c)
    new AsyncHttpClient(cacheProvider, config)
  }

  override def underlying[T] = asyncHttpClient.asInstanceOf[T]

  override private[libs] def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = asyncHttpClient.executeRequest(request, handler)

  override def close() = asyncHttpClient.close()

  override def url(url: String): StandaloneWSRequest = AhcWSRequest(this, url, "GET", EmptyBody, Map(), Map(), None, None, None, None, None, None, None)
}

object CachingNingWSClient {
  /**
   * Convenient factory method that uses a [[play.api.libs.ws.WSClientConfig]] value for configuration instead of an [[AsyncHttpClientConfig]].
   *
   * Typical usage:
   *
   * {{{
   *   val client = CachingNingWSClient()
   *   val request = client.url(someUrl).get()
   *   request.foreach { response =>
   *     doSomething(response)
   *     client.close()
   *   }
   * }}}
   *
   * @param config configuration settings
   */
  def apply(config: NingWSClientConfig = NingWSClientConfig()): CachingNingWSClient = {
    val asyncClientConfig = new NingAsyncHttpClientConfigBuilder(config).build()
    val ningCache = NingWSCache(asyncClientConfig)
    val client = new CachingNingWSClient(asyncClientConfig, ningCache)
    new SystemConfiguration().configure(config.wsClientConfig)
    client
  }
}
