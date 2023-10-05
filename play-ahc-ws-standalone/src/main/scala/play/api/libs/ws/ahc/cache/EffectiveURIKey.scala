/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime

import org.playframework.cachecontrol.HeaderName
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
    expiresAt: Option[ZonedDateTime]
) {

  /**
   * Has the entry expired yet?
   */
  def isExpired: Boolean = expiresAt.exists(_.toInstant.isBefore(Instant.now()))
}
