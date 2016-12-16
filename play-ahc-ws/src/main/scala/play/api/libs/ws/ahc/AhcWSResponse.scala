package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils
import play.shaded.ahc.org.asynchttpclient.{Response => AHCResponse}
import play.api.Play
import play.api.libs.json.JsValue
import play.api.libs.ws._

import scala.xml.Elem

case class AhcWSResponse(plainResponse: StandaloneAhcWSResponse) extends WSResponse {

  def this(ahcResponse: AHCResponse) = {
    this(StandaloneAhcWSResponse(ahcResponse))
  }

  /**
   * The response body as String.
   */
  lazy val body: String = {
    // RFC-2616#3.7.1 states that any text/* mime type should default to ISO-8859-1 charset if not
    // explicitly set, while Plays default encoding is UTF-8.  So, use UTF-8 if charset is not explicitly
    // set and content type is not text/*, otherwise default to ISO-8859-1
    val contentType = Option(plainResponse.ahcResponse.getContentType).getOrElse("application/octet-stream")
    val charset = Option(HttpUtils.parseCharset(contentType)).getOrElse {
      if (contentType.startsWith("text/"))
        HttpUtils.DEFAULT_CHARSET
      else
        StandardCharsets.UTF_8
    }
    plainResponse.ahcResponse.getResponseBody(charset)
  }

  /**
   * The response body as Xml.
   */
  override lazy val xml: Elem = Play.XML.loadString(body)

  /**
   * Return the current headers of the request being constructed
   */
  override def allHeaders: Map[String, Seq[String]] = plainResponse.allHeaders

  /**
   * Get the underlying response object.
   */
  override def underlying[T]: T = plainResponse.underlying[T]

  /**
   * The response status code.
   */
  override def status: Int = plainResponse.status

  /**
   * The response status message.
   */
  override def statusText: String = plainResponse.statusText

  /**
   * Get a response header.
   */
  override def header(key: String): Option[String] = plainResponse.header(key)

  /**
   * Get all the cookies.
   */
  override def cookies: Seq[WSCookie] = plainResponse.cookies

  /**
   * Get only one cookie, using the cookie name.
   */
  override def cookie(name: String): Option[WSCookie] = plainResponse.cookie(name)

  /**
   * The response body as Json.
   */
  override def json: JsValue = plainResponse.json

  /**
   * The response body as a byte string.
   */
  override def bodyAsBytes: ByteString = plainResponse.bodyAsBytes
}
