package play.api.libs.ws

import java.io.File
import java.net.URI

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * A WS Request builder.
 */
trait StandaloneWSRequest {
  type Self <: StandaloneWSRequest
  type Response <: StandaloneWSResponse

  /**
   * The base URL for this request
   */
  val url: String

  /**
   * The URI for this request
   */
  val uri: URI

  /**
   * The method for this request
   */
  val method: String

  /**
   * The body of this request
   */
  val body: WSBody

  /**
   * The headers for this request
   */
  val headers: Map[String, Seq[String]]

  /**
   * The query string for this request
   */
  val queryString: Map[String, Seq[String]]

  /**
   * A calculator of the signature for this request
   */
  val calc: Option[WSSignatureCalculator]

  /**
   * The authentication this request should use
   */
  val auth: Option[(String, String, WSAuthScheme)]

  /**
   * Whether this request should follow redirects
   */
  val followRedirects: Option[Boolean]

  /**
   * The timeout for the request
   */
  val requestTimeout: Option[Int]

  /**
   * The virtual host this request will use
   */
  val virtualHost: Option[String]

  /**
   * The proxy server this request will use
   */
  val proxyServer: Option[WSProxyServer]

  /**
   * sets the signature calculator for the request
   * @param calc
   */
  def sign(calc: WSSignatureCalculator): Self

  /**
   * sets the authentication realm
   */
  def withAuth(username: String, password: String, scheme: WSAuthScheme): Self

  /**
   * adds any number of HTTP headers
   * @param hdrs
   */
  def withHeaders(hdrs: (String, String)*): Self

  /**
   * adds any number of query string parameters to this request
   */
  def withQueryString(parameters: (String, String)*): Self

  /**
   * Sets whether redirects (301, 302) should be followed automatically
   */
  def withFollowRedirects(follow: Boolean): Self

  /**
   * Sets the maximum time you expect the request to take.
   * Use Duration.Inf to set an infinite request timeout.
   * Warning: a stream consumption will be interrupted when this time is reached unless Duration.Inf is set.
   */
  def withRequestTimeout(timeout: Duration): Self

  /**
   * Sets the virtual host to use in this request
   */
  def withVirtualHost(vh: String): Self

  /**
   * Sets the proxy server to use in this request
   */
  def withProxyServer(proxyServer: WSProxyServer): Self

  /**
   * Sets the body for this request
   */
  def withBody(body: WSBody): Self

  /**
   * Sets the method for this request
   */
  def withMethod(method: String): Self

  /**
   * performs a get
   */
  def get(): Future[Response]

  /**
   * Perform a PATCH on the request asynchronously.
   * Request body won't be chunked
   */
  def patch(body: File): Future[Response]

  /**
   * Perform a POST on the request asynchronously.
   * Request body won't be chunked
   */
  def post(body: File): Future[Response]

  /**
   * Perform a PUT on the request asynchronously.
   * Request body won't be chunked
   */
  def put(body: File): Future[Response]

  /**
   * Perform a DELETE on the request asynchronously.
   */
  def delete(): Future[Response]

  /**
   * Perform a HEAD on the request asynchronously.
   */
  def head(): Future[Response]

  /**
   * Perform a OPTIONS on the request asynchronously.
   */
  def options(): Future[Response]

  def execute(method: String): Future[Response]

  /**
   * Execute this request
   */
  def execute(): Future[Response]

  /**
   * Execute this request and stream the response body.
   */
  def stream(): Future[StreamedResponse]
}
