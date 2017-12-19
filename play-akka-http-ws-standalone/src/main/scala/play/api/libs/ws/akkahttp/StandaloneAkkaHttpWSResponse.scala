/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{ Cookie, HttpCookie, `Set-Cookie` }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.{ DefaultWSCookie, StandaloneWSResponse, WSCookie }

import scala.concurrent.Await
import scala.concurrent.duration._

private[akkahttp] object StandaloneAkkaHttpWSResponse {
  def apply(resp: HttpResponse)(implicit sys: ActorSystem, mat: Materializer) = new StandaloneAkkaHttpWSResponse(resp)
}

final class StandaloneAkkaHttpWSResponse private (val response: HttpResponse)(implicit val sys: ActorSystem, val mat: Materializer) extends StandaloneWSResponse {

  // FIXME make configurable
  final val UnmarshalTimeout = 1.second

  private lazy val strictResponse = response.toStrict(UnmarshalTimeout)(sys.dispatcher, mat)

  /**
   * Return the current headers for this response.
   */
  override def headers: Map[String, Seq[String]] =
    response.headers
      .groupBy(_.name)
      .mapValues(_.map(_.value()))

  /**
   * Get the underlying response object.
   */
  override def underlying[T]: T = response.asInstanceOf[T]

  /**
   * The response status code.
   */
  override def status: Int = response.status.intValue()

  /**
   * The response status message.
   */
  override def statusText: String = response.status.reason

  /**
   * Get all the cookies.
   */
  override def cookies: Seq[WSCookie] = response.headers.collect {
    case `Set-Cookie`(cookie) => DefaultWSCookie(cookie.name, cookie.value)
  }

  /**
   * Get only one cookie, using the cookie name.
   */
  override def cookie(name: String): Option[WSCookie] = cookies.find(_.name == name)

  /**
   * The response body decoded as String, using a simple algorithm to guess the encoding.
   *
   * This decodes the body to a string representation based on the following algorithm:
   *
   *  1. Look for a "charset" parameter on the Content-Type. If it exists, set `charset` to its value and go to step 3.
   *  2. If the Content-Type is of type "text", set charset to "ISO-8859-1"; else set `charset` to "UTF-8".
   *  3. Decode the raw bytes of the body using `charset`.
   *
   * Note that this does not take into account any special cases for specific content types. For example, for
   * application/json, we do not support encoding autodetection and will trust the charset parameter if provided..
   *
   * @return the response body parsed as a String using the above algorithm.
   */
  override def body: String =
    bodyAsBytes.utf8String

  /**
   * @return The response body as ByteString.
   */
  override def bodyAsBytes: ByteString = {
    import sys.dispatcher
    import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteStringUnmarshaller
    Await.result(strictResponse.flatMap(Unmarshal(_).to[ByteString]), UnmarshalTimeout)
  }

  /**
   * @return the response as a source of bytes
   */
  override def bodyAsSource: Source[ByteString, _] = response.entity.dataBytes
}
