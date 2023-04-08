/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.WSCookie
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames._
import play.shaded.ahc.io.netty.handler.codec.http.cookie.ClientCookieDecoder
import play.shaded.ahc.io.netty.handler.codec.http.cookie.Cookie
import play.shaded.ahc.io.netty.handler.codec.http.cookie.DefaultCookie

trait CookieBuilder extends WSCookieConverter {
  def buildCookies(headers: Map[String, scala.collection.Seq[String]]): scala.collection.Seq[WSCookie] = {
    val option = headers.get(SET_COOKIE2.toString).orElse(headers.get(SET_COOKIE.toString))
    option
      .map { cookiesHeaders =>
        for {
          value <- cookiesHeaders
          Some(c) = Some(
            if (useLaxCookieEncoder) ClientCookieDecoder.LAX.decode(value)
            else ClientCookieDecoder.STRICT.decode(value)
          )
        } yield asCookie(c)
      }
      .getOrElse(Seq.empty)
  }

  def useLaxCookieEncoder: Boolean
}

/**
 * Converts between AHC cookie and the WS cookie.
 */
trait WSCookieConverter {

  def asCookie(cookie: WSCookie): Cookie = {
    val c = new DefaultCookie(cookie.name, cookie.value)
    c.setWrap(false)
    c.setDomain(cookie.domain.orNull)
    c.setPath(cookie.path.orNull)
    c.setMaxAge(cookie.maxAge.getOrElse(-1L))
    c.setSecure(cookie.secure)
    c.setHttpOnly(cookie.httpOnly)
    c
  }

  def asCookie(c: Cookie): WSCookie = {
    DefaultWSCookie(
      name = c.name,
      value = c.value,
      domain = Option(c.domain),
      path = Option(c.path),
      maxAge = Option(c.maxAge).filterNot(_ < 0),
      secure = c.isSecure,
      httpOnly = c.isHttpOnly
    )
  }
}
