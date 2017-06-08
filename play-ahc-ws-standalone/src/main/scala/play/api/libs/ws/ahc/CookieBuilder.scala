package play.api.libs.ws.ahc

import play.api.libs.ws.WSCookie
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names._
import play.shaded.ahc.org.asynchttpclient.cookie.CookieDecoder

trait CookieBuilder {
  def buildCookies(headers: Map[String, Seq[String]]): Seq[WSCookie] = {
    val option = headers.get(SET_COOKIE2).orElse(headers.get(SET_COOKIE))
    option.map { cookiesHeaders =>
      for {
        value <- cookiesHeaders
        Some(c) = Option(CookieDecoder.decode(value))
      } yield new AhcWSCookie(c)
    }.getOrElse(Seq.empty)
  }
}
