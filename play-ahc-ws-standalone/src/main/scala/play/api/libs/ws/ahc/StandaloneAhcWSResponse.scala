/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.{ DefaultBodyReadables, StandaloneWSResponse, WSCookie }
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }

import scala.collection.JavaConverters._

/**
 * A WS HTTP response backed by org.asynchttpclient.Response.
 */
class StandaloneAhcWSResponse(ahcResponse: AHCResponse) extends StandaloneWSResponse
    with DefaultBodyReadables
    with AhcUtilities {

  override lazy val headers: Map[String, Seq[String]] = headersToMap(ahcResponse.getHeaders)

  override def underlying[T]: T = ahcResponse.asInstanceOf[T]

  override def status: Int = ahcResponse.getStatusCode

  override def statusText: String = ahcResponse.getStatusText

  override lazy val cookies: Seq[WSCookie] = ahcResponse.getCookies.asScala.map(new AhcWSCookie(_))

  override def cookie(name: String): Option[WSCookie] = cookies.find(_.name == Option(name))

  override def toString: String = s"StandaloneAhcWSResponse($status, $statusText)"

  /**
   * The response body as String.
   */
  override lazy val body: String = {
    // https://tools.ietf.org/html/rfc7231#section-3.1.1.3
    // https://tools.ietf.org/html/rfc7231#appendix-B
    // The default charset of ISO-8859-1 for text media types has been
    // removed; the default is now whatever the media type definition says.
    ahcResponse.getResponseBody()
  }

  /**
   * The response body as a byte string.
   */
  override lazy val bodyAsBytes: ByteString = ByteString.fromArray(underlying[AHCResponse].getResponseBodyAsBytes)

  override lazy val bodyAsSource: Source[ByteString, _] = Source.single(bodyAsBytes)
}

object StandaloneAhcWSResponse {
  def apply(ahcResponse: AHCResponse): StandaloneAhcWSResponse = {
    new StandaloneAhcWSResponse(ahcResponse)
  }
}
