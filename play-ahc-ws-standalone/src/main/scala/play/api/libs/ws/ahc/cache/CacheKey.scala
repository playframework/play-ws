package play.api.libs.ws.ahc.cache

import java.net.URI

import com.typesafe.play.cachecontrol.HeaderName
import org.joda.time.DateTime
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

/**
 * A cache entry with an optional expiry time
 */
case class CacheEntry(
  response: CacheableResponse,
    requestMethod: String,
    nominatedHeaders: Map[HeaderName, Seq[String]],
    expiresAt: Option[DateTime]) {

  /**
   * Has the entry expired yet?
   */
  def isExpired: Boolean = expiresAt.exists(_.isBeforeNow)
}
