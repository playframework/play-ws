package play.api.libs.ws.ahc.cache

import java.net.URI

import play.shaded.ahc.org.asynchttpclient._

case class CacheKey(method: String, uri: URI) {
  override def toString: String = method + " " + uri.toString
}

object CacheKey {
  def apply(request: Request): CacheKey = {
    require(request != null)
    CacheKey(request.getMethod, request.getUri.toJavaNetURI)
  }
}
