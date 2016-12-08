package play.api.libs.ws.ahc

import java.io.File
import java.net.URI

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.Writeable
import play.api.libs.ws._
import play.api.mvc.MultipartFormData
import play.core.formatters.Multipart

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 *
 */
case class AhcWSRequest(underlying: StandaloneAhcWSRequest) extends WSRequest {
  override type Self = WSRequest
  override type Response = WSResponse

  import scala.concurrent.ExecutionContext.Implicits._

  /**
   * The URI for this request
   */
  override val uri: URI = underlying.uri

  /**
   * The base URL for this request
   */
  override val url: String = underlying.url
  /**
   * The method for this request
   */
  override val method: String = underlying.method
  /**
   * The body of this request
   */
  override val body: WSBody = underlying.body

  /**
   * The headers for this request
   */
  override val headers: Map[String, Seq[String]] = underlying.headers

  /**
   * The query string for this request
   */
  override val queryString: Map[String, Seq[String]] = underlying.queryString

  /**
   * A calculator of the signature for this request
   */
  override val calc: Option[WSSignatureCalculator] = underlying.calc
  /**
   * The authentication this request should use
   */
  override val auth: Option[(String, String, WSAuthScheme)] = underlying.auth

  /**
   * Whether this request should follow redirects
   */
  override val followRedirects: Option[Boolean] = underlying.followRedirects

  /**
   * The timeout for the request
   */
  override val requestTimeout: Option[Int] = underlying.requestTimeout

  /**
   * The virtual host this request will use
   */
  override val virtualHost: Option[String] = underlying.virtualHost

  /**
   * The proxy server this request will use
   */
  override val proxyServer: Option[WSProxyServer] = underlying.proxyServer

  override def sign(calc: WSSignatureCalculator): Self = toWSRequest {
    underlying.sign(calc)
  }

  override def withAuth(username: String, password: String, scheme: WSAuthScheme): Self = toWSRequest {
    underlying.withAuth(username, password, scheme)
  }

  override def withHeaders(hdrs: (String, String)*): Self = toWSRequest {
    underlying.withHeaders(hdrs: _*)
  }

  override def withQueryString(parameters: (String, String)*): Self = toWSRequest {
    underlying.withQueryString(parameters: _*)
  }

  override def withFollowRedirects(follow: Boolean): Self = toWSRequest {
    underlying.withFollowRedirects(follow)
  }

  override def withRequestTimeout(timeout: Duration): Self = toWSRequest {
    underlying.withRequestTimeout(timeout)
  }

  override def withVirtualHost(vh: String): Self = toWSRequest {
    underlying.withVirtualHost(vh)
  }

  override def withProxyServer(proxyServer: WSProxyServer): Self = toWSRequest {
    underlying.withProxyServer(proxyServer)
  }

  override def withBody(body: WSBody): Self = toWSRequest {
    underlying.withBody(body)
  }

  override def withMethod(method: String): Self = toWSRequest {
    underlying.withMethod(method)
  }

  /**
   * Sets the body for this request
   */
  def withBody[T](body: T)(implicit wrt: Writeable[T]): WSRequest = {
    val wsBody = InMemoryBody(wrt.transform(body))
    if (headers.contains("Content-Type")) {
      withBody(wsBody)
    } else {
      wrt.contentType.fold(withBody(wsBody)) { contentType =>
        withBody(wsBody).withHeaders("Content-Type" -> contentType)
      }
    }
  }

  /**
   * Sets a multipart body for this request
   */
  def withBody(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): WSRequest = {
    val boundary = Multipart.randomBoundary()
    val contentType = s"multipart/form-data; boundary=$boundary"
    withBody(StreamedBody(Multipart.transform(body, boundary))).withHeaders("Content-Type" -> contentType)
  }

  /**
   * performs a get
   */
  def get(): Future[WSResponse] = withMethod("GET").execute()

  /**
   * Perform a PATCH on the request asynchronously.
   */
  def patch[T](body: T)(implicit wrt: Writeable[T]): Future[WSResponse] =
    withBody(body).execute("PATCH")

  /**
   * Perform a PATCH on the request asynchronously.
   * Request body won't be chunked
   */
  override def patch(body: File): Future[Response] = {
    withBody(FileBody(body)).execute("PATCH")
  }

  /**
   * Perform a PATCH on the request asynchronously.
   */
  def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] = {
    withMethod("PATCH").withBody(body).execute()
  }

  /**
   * Perform a POST on the request asynchronously.
   */
  def post[T](body: T)(implicit wrt: Writeable[T]): Future[WSResponse] =
    withMethod("POST").withBody(body).execute()

  /**
   * Perform a POST on the request asynchronously.
   * Request body won't be chunked
   */
  def post(body: File): Future[WSResponse] = withBody(FileBody(body)).execute("POST")

  /**
   * Perform a POST on the request asynchronously.
   */
  def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
    withMethod("POST").withBody(body).execute()

  /**
   * Perform a PUT on the request asynchronously.
   */
  def put[T](body: T)(implicit wrt: Writeable[T]): Future[WSResponse] =
    withMethod("PUT").withBody(body).execute()

  /**
   * Perform a PUT on the request asynchronously.
   * Request body won't be chunked
   */
  def put(body: File): Future[WSResponse] = withBody(FileBody(body)).execute("PUT")

  /**
   * Perform a PUT on the request asynchronously.
   */
  def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] = {
    withMethod("PUT").withBody(body).execute()
  }

  /**
   * Perform a DELETE on the request asynchronously.
   */
  def delete(): Future[WSResponse] = withMethod("DELETE").execute()

  /**
   * Perform a HEAD on the request asynchronously.
   */
  def head(): Future[WSResponse] = withMethod("HEAD").execute()

  /**
   * Perform a OPTIONS on the request asynchronously.
   */
  def options(): Future[WSResponse] = withMethod("OPTIONS").execute()

  override def stream(): Future[StreamedResponse] = {
    Streamed.execute(underlying.client.underlying, underlying.buildRequest())
  }

  override def execute(method: String): Future[Response] = {
    withMethod(method).execute()
  }

  override def execute(): Future[Response] = {
    underlying.execute().map { f =>
      AhcWSResponse(f.asInstanceOf[StandaloneAhcWSResponse])
    }
  }

  private def toWSRequest(request: StandaloneWSRequest): Self = {
    AhcWSRequest(request.asInstanceOf[StandaloneAhcWSRequest])
  }

}
