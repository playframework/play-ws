/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.reactivestreams.Publisher
import play.api.libs.ws.{ StandaloneWSResponse, WSCookie }
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart

/**
 * A streamed response containing a response header and a streamable body.
 *
 * Note that this is only usable with a stream call, i.e.
 *
 * {{{
 * class MyClass extends StreamedBodyReadable {
 *   ws.url("http://example.com").stream().map { response =>
 *      val source = response.body[Source[ByteString, NotUsed]]
 *      ...
 *   }
 * }
 * }}}
 */
class StreamedResponse(
    client: StandaloneAhcWSClient,
    val status: Int,
    val statusText: String,
    val uri: java.net.URI,
    val headers: Map[String, Seq[String]],
    publisher: Publisher[HttpResponseBodyPart],
    val useLaxCookieEncoder: Boolean) extends StandaloneWSResponse with CookieBuilder {

  /**
   * Get the underlying response object.
   */
  override def underlying[T]: T = publisher.asInstanceOf[T]

  /**
   * Get all the cookies.
   */
  override lazy val cookies: Seq[WSCookie] = buildCookies(headers)

  /**
   * Get only one cookie, using the cookie name.
   */
  override def cookie(name: String): Option[WSCookie] = cookies.find(_.name == name)

  /**
   * THIS IS A BLOCKING OPERATION. It should not be used in production.
   *
   * Note that this is not a charset aware operation, as the stream does not have access to the underlying machinery
   * that disambiguates responses.
   *
   * @return the body as a String
   */
  override lazy val body: String = bodyAsBytes.decodeString(AhcWSUtils.getCharset(contentType))

  /**
   * THIS IS A BLOCKING OPERATION. It should not be used in production.
   *
   * Note that this is not a charset aware operation, as the stream does not have access to the underlying machinery
   * that disambiguates responses.
   *
   * @return the body as a ByteString
   */
  override lazy val bodyAsBytes: ByteString = client.blockingToByteString(bodyAsSource)

  override lazy val bodyAsSource: Source[ByteString, _] = {
    Source.fromPublisher(publisher).map((bodyPart: HttpResponseBodyPart) => ByteString.fromArray(bodyPart.getBodyPartBytes))
  }

}
