package play.api.libs.ws.ahc.cache

import javax.cache.Caching
import javax.cache.configuration.FactoryBuilder.SingletonFactory
import javax.cache.configuration.{ Configuration, MutableConfiguration }
import javax.cache.expiry.EternalExpiryPolicy

import play.shaded.ahc.io.netty.handler.codec.http.{ DefaultHttpHeaders, HttpHeaders }
import play.shaded.ahc.org.asynchttpclient.{ Request, RequestBuilder }

/**
 * Utility methods to make building requests and responses easier.
 */
trait CacheBuilderMethods {

  def generateCache: AhcHttpCache = {
    val cacheManager = Caching.getCachingProvider.getCacheManager
    val configuration: Configuration[CacheKey, CacheEntry] = new MutableConfiguration().setTypes(classOf[CacheKey], classOf[CacheEntry]).setStoreByValue(false).setExpiryPolicyFactory(new SingletonFactory(new EternalExpiryPolicy()))
    val simpleCache = cacheManager.createCache[CacheKey, CacheEntry, Configuration[CacheKey, CacheEntry]]("play-ws-cache", configuration)

    AhcHttpCache(simpleCache)
  }

  def generateRequest(url: String)(block: HttpHeaders => HttpHeaders): Request = {
    val requestBuilder = new RequestBuilder()
    val requestHeaders = block(new DefaultHttpHeaders())

    requestBuilder
      .setUrl(url)
      .setHeaders(requestHeaders)
      .build
  }

}
