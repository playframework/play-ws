/*
  * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import play.shaded.ahc.org.asynchttpclient.cookie.{Cookie => AHCCookie}
import play.api.libs.ws._

/**
 * The Ahc implementation of a WS cookie.
 */
private class AhcWSCookie(ahcCookie: AHCCookie) extends WSCookie {

  private def noneIfEmpty(value: String): Option[String] = {
    if (value.isEmpty) None else Some(value)
  }

  /**
   * The underlying cookie object for the client.
   */
  def underlying[T] = ahcCookie.asInstanceOf[T]

  /**
   * The domain.
   */
  def domain: String = ahcCookie.getDomain

  /**
   * The cookie name.
   */
  def name: Option[String] = noneIfEmpty(ahcCookie.getName)

  /**
   * The cookie value.
   */
  def value: Option[String] = noneIfEmpty(ahcCookie.getValue)

  /**
   * The path.
   */
  def path: String = ahcCookie.getPath

  /**
   * The maximum age.
   */
  def maxAge: Option[Long] = if (ahcCookie.getMaxAge <= -1) None else Some(ahcCookie.getMaxAge)

  /**
   * If the cookie is secure.
   */
  def secure: Boolean = ahcCookie.isSecure

  /*
   * Cookie ports should not be used; cookies for a given host are shared across
   * all the ports on that host.
   */

  override def toString: String = ahcCookie.toString
}



