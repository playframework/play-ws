/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import java.io.IOException
import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils
import play.shaded.ahc.org.asynchttpclient.{Response => AHCResponse}
import play.api.libs.ws.{StandaloneWSResponse, WSCookie}

import scala.xml.Elem

/**
 * A WS HTTP response.
 */
case class StandaloneAhcWSResponse(ahcResponse: AHCResponse) extends StandaloneWSResponse {

  import play.api.libs.json._

  /**
   * Return the headers of the response as a case-insensitive map
   */
  lazy val allHeaders: Map[String, Seq[String]] = {
    val headers: HttpHeaders = ahcResponse.getHeaders
    StandaloneAhcWSRequest.ahcHeadersToMap(headers)
  }

  /**
   * @return The underlying response object.
   */
  def underlying[T] = ahcResponse.asInstanceOf[T]

  /**
   * The response status code.
   */
  def status: Int = ahcResponse.getStatusCode

  /**
   * The response status message.
   */
  def statusText: String = ahcResponse.getStatusText

  /**
   * Get a response header.
   */
  def header(key: String): Option[String] = Option(ahcResponse.getHeader(key))

  /**
   * Get all the cookies.
   */
  def cookies: Seq[WSCookie] = {
    import scala.collection.JavaConverters._
    ahcResponse.getCookies.asScala.map(new AhcWSCookie(_))
  }

  /**
   * Get only one cookie, using the cookie name.
   */
  def cookie(name: String): Option[WSCookie] = cookies.find(_.name == Option(name))

  /**
   * The response body as String.
   */
  lazy val body: String = {
    // RFC-2616#3.7.1 states that any text/* mime type should default to ISO-8859-1 charset if not
    // explicitly set, while Plays default encoding is UTF-8.  So, use UTF-8 if charset is not explicitly
    // set and content type is not text/*, otherwise default to ISO-8859-1
    val contentType = Option(ahcResponse.getContentType).getOrElse("application/octet-stream")
    val charset = Option(HttpUtils.parseCharset(contentType)).getOrElse {
      if (contentType.startsWith("text/"))
        HttpUtils.DEFAULT_CHARSET
      else
        StandardCharsets.UTF_8
    }
    ahcResponse.getResponseBody(charset)
  }

  /**
   * The response body as Xml.
   */
  lazy val xml: Elem = XML.instance.loadString(body)

  /**
   * The response body as Json.
   */
  lazy val json: JsValue = Json.parse(ahcResponse.getResponseBodyAsBytes)

  /**
   * The response body as a byte string.
   */
  @throws(classOf[IOException])
  def bodyAsBytes: ByteString = ByteString(ahcResponse.getResponseBodyAsBytes)

  override def toString: String =
    s"AhcWSResponse($status, $statusText)"

}
