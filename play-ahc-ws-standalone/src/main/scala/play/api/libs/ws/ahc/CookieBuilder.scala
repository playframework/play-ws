package play.api.libs.ws.ahc

import play.api.libs.ws.{ DefaultWSCookie, WSCookie }
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names._
import play.shaded.ahc.org.asynchttpclient.cookie.{ Cookie, CookieDecoder }

trait CookieBuilder extends WSCookieConverter {
  def buildCookies(headers: Map[String, Seq[String]]): Seq[WSCookie] = {
    val option = headers.get(SET_COOKIE2).orElse(headers.get(SET_COOKIE))
    option.map { cookiesHeaders =>
      for {
        value <- cookiesHeaders
        Some(c) = Option(CookieDecoder.decode(value))
      } yield asCookie(c)
    }.getOrElse(Seq.empty)
  }

}

/**
 * Converts between AHC cookie and the WS cookie.
 */
trait WSCookieConverter {

  def asCookie(cookie: WSCookie): Cookie = {
    Cookie.newValidCookie(
      cookie.name,
      cookie.value,
      false,
      cookie.domain.orNull,
      cookie.path.orNull,
      cookie.maxAge.getOrElse(-1L),
      cookie.secure,
      cookie.httpOnly)
  }

  def asCookie(c: Cookie): WSCookie = {
    DefaultWSCookie(
      name = c.getName,
      value = c.getValue,
      domain = Option(c.getDomain),
      path = Option(c.getPath),
      maxAge = Option(c.getMaxAge).filterNot(_ < 0),
      secure = c.isSecure,
      httpOnly = c.isHttpOnly
    )
  }
}