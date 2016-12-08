package play.api.libs.ws

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.Writeable
import play.api.mvc.MultipartFormData

import scala.concurrent.Future

/**
 * A WS Request builder.
 */
trait WSRequest extends StandaloneWSRequest {
  override type Self <: WSRequest
  override type Response <: WSResponse

  /**
   * Sets the body for this request
   */
  def withBody[T](body: T)(implicit wrt: Writeable[T]): Self

  /**
   * Sets a multipart body for this request
   */
  def withBody(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Self

  /**
   * Perform a PATCH on the request asynchronously.
   */
  def patch[T](body: T)(implicit wrt: Writeable[T]): Future[Response]
  
  /**
   * Perform a PATCH on the request asynchronously.
   */
  def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response]

  /**
   * Perform a PUT on the request asynchronously.
   */
  def post[T](body: T)(implicit wrt: Writeable[T]): Future[Response]

  /**
   * Perform a POST on the request asynchronously.
   */
  def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response]

  /**
   * Perform a PUT on the request asynchronously.
   */
  def put[T](body: T)(implicit wrt: Writeable[T]): Future[Response]

  /**
   * Perform a PUT on the request asynchronously.
   */
  def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response]

}
