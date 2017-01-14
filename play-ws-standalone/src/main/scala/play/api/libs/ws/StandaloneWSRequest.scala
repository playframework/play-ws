/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import java.io.File
import java.net.URI

import play.api.libs.ws

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * A WS Request builder.
 */
trait StandaloneWSRequest {
  // There is an issue with type members, because when you stack `Self` references,
  // they don't have a connection to each other that ties them back to the parent
  // `Self` reference.  The way around this is to do F-bounded polymorphism directly
  // on the type member, which roots it to the parent and doesn't mess up the type
  // signature the way that a self-recursive type parameter would.  The implementation
  // will always bind this to a relevant type, so we only have to make the compiler
  // happy here.
  type Self <: StandaloneWSRequest { type Self <: StandaloneWSRequest.this.Self }
  type Response <: StandaloneWSResponse

  /**
   * The base URL for this request
   */
  def url: String

  /**
   * The URI for this request
   */
  def uri: URI

  /**
   * The method for this request
   */
  def method: String

  /**
   * The body of this request
   */
  def body: WSBody

  /**
   * The headers for this request
   */
  def headers: Map[String, Seq[String]]

  /**
   * The query string for this request
   */
  def queryString: Map[String, Seq[String]]

  /**
   * A calculator of the signature for this request
   */
  def calc: Option[WSSignatureCalculator]

  /**
   * The authentication this request should use
   */
  def auth: Option[(String, String, WSAuthScheme)]

  /**
   * Whether this request should follow redirects
   */
  def followRedirects: Option[Boolean]

  /**
   * The timeout for the request
   */
  def requestTimeout: Option[Int]

  /**
   * The virtual host this request will use
   */
  def virtualHost: Option[String]

  /**
   * The proxy server this request will use
   */
  def proxyServer: Option[WSProxyServer]

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
   * Adds a filter to the request that can transform the request for subsequent filters.
   */
  def withRequestFilter(filter: WSRequestFilter[Self, Response]): Self

  /**
   * Sets the virtual host to use in this request
   */
  def withVirtualHost(vh: String): Self

  /**
   * Sets the proxy server to use in this request
   */
  def withProxyServer(proxyServer: WSProxyServer): Self

  /**
   * Sets the method for this request
   */
  def withMethod(method: String): Self

  /**
   * Sets the body for this request
   */
  def withBody(body: WSBody): Self

  /**
   * Sets the body for this request
   */
  def withBody(file: File): Self

  /**
   * Sets the body for this request.
   */
  def withBody[T: BodyWritable](body: T): Self

  /**
   * Performs a GET.
   */
  def get(): Future[Response]

  /**
   *
   */
  def patch[T: BodyWritable](body: T): Future[Response]

  def patch(body: File): Future[Response]

  /**
   *
   */
  def post[T: BodyWritable](body: T): Future[Response]

  def post(body: File): Future[Response]

  /**
   *
   */
  def put[T: BodyWritable](body: T): Future[Response]

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

trait WSRequestExecutor[-Request, +Response] {
  def execute(request: Request): Future[Response]
}

trait WSRequestFilter[Request <: StandaloneWSRequest, Response <: StandaloneWSResponse] {
  def apply(next: WSRequestExecutor[Request, Response]): WSRequestExecutor[Request, Response]
}
