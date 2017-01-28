package play.api.libs.ws.ning.cache

import play.shaded.ahc.org.asynchttpclient.{ RequestBuilder, Request, HttpHeader }
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder

/**
 * Utility methods to make building requests and responses easier.
 */
trait NingBuilderMethods {

  def generateCache: AhcWSCache = {
    val builder = new NingAsyncHttpClientConfigBuilder()
    AhcWSCache(builder.build())
  }

  def generateRequest(url: String)(block: HttpHeader => HttpHeader): Request = {
    val requestBuilder = new RequestBuilder()
    val requestHeaders = block(new HttpHeader())

    requestBuilder
      .setUrl(url)
      .setHeaders(requestHeaders)
      .build
  }

}
