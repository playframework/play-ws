package play.api.libs.ws.ning.cache

import play.api.libs.ws.ahc.AhcConfigBuilder
import play.shaded.ahc.io.netty.handler.codec.http.{ DefaultHttpHeaders, HttpHeaders }
import play.shaded.ahc.org.asynchttpclient.{ Request, RequestBuilder }

/**
 * Utility methods to make building requests and responses easier.
 */
trait NingBuilderMethods {

  def generateCache: AhcWSCache = AhcWSCache()

  def generateRequest(url: String)(block: HttpHeaders => HttpHeaders): Request = {
    val requestBuilder = new RequestBuilder()
    val requestHeaders = block(new DefaultHttpHeaders())

    requestBuilder
      .setUrl(url)
      .setHeaders(requestHeaders)
      .build
  }

}
