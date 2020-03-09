/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.net.URI

import com.typesafe.play.cachecontrol.HeaderName
import org.joda.time.DateTime
import play.shaded.ahc.org.asynchttpclient._

case class EffectiveURIKey(method: String, uri: URI) {
  override def toString: String = method + " " + uri.toString
}

object EffectiveURIKey {
  def apply(request: Request): EffectiveURIKey = {
    require(request != null)
    EffectiveURIKey(request.getMethod, request.getUri.toJavaNetURI)
  }
}

/**
 * A cache entry with an optional expiry time
 */
case class ResponseEntry(
    response: CacheableResponse,
    requestMethod: String,
    nominatedHeaders: Map[HeaderName, Seq[String]],
    expiresAt: Option[DateTime]) {

  /**
   * Has the entry expired yet?
   */
  def isExpired: Boolean = expiresAt.exists(_.isBeforeNow)
}
