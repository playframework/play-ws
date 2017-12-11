package play.api.libs.ws.akkahttp

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import play.api.libs.ws.{ StandaloneWSResponse, WSCookie }

import scala.concurrent.Await
import scala.concurrent.duration._

private[akkahttp] object StandaloneAkkaHttpWSResponse {
  def apply(resp: HttpResponse)(implicit mat: Materializer) = new StandaloneAkkaHttpWSResponse(resp)
}

final class StandaloneAkkaHttpWSResponse private (val response: HttpResponse)(implicit val mat: Materializer) extends StandaloneWSResponse {

  final val UnmarshalTimeout = 1.second

  /**
   * Return the current headers for this response.
   */
  override def headers: Map[String, Seq[String]] = ???

  /**
   * Get the underlying response object.
   */
  override def underlying[T]: T = ???

  /**
   * The response status code.
   */
  override def status: Int = ???

  /**
   * The response status message.
   */
  override def statusText: String = ???

  /**
   * Get all the cookies.
   */
  override def cookies: Seq[WSCookie] = ???

  /**
   * Get only one cookie, using the cookie name.
   */
  override def cookie(name: String): Option[WSCookie] = ???

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
  override def body: String = {
    import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller
    Await.result(Unmarshal(response).to[String], UnmarshalTimeout)
  }

  /**
   * @return The response body as ByteString.
   */
  override def bodyAsBytes = ???

  /**
   * @return the response as a source of bytes
   */
  override def bodyAsSource = response.entity.dataBytes
}
