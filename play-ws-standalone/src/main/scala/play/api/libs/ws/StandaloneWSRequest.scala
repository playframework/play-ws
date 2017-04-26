/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import java.io.File
import java.net.URI

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
   * The content type for this request, if any is defined.
   */
  def contentType: Option[String]

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
   * The cookies for this request
   */
  def cookies: Seq[WSCookie]

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
   * @param calc the signature calculator
   */
  def sign(calc: WSSignatureCalculator): Self

  /**
   * sets the authentication realm
   */
  def withAuth(username: String, password: String, scheme: WSAuthScheme): Self

  /**
   * Returns this request with the given headers, discarding the existing ones.
   *
   * @param headers the headers to be used
   */
  def setHeaders(headers: (String, String)*): Self

  /**
   * Returns this request with the given headers, preserving the existing ones.
   *
   * @param headers the headers to be used
   */
  @deprecated("Use setHeaders or addHeaders", "1.0.0")
  def withHeaders(headers: (String, String)*): Self = addHeaders(headers: _*)

  /**
   * Returns this request with the given headers, preserving the existing ones.
   *
   * @param hdrs the headers to be added
   */
  def addHeaders(hdrs: (String, String)*): Self = {
    val newHeaders = headers.toList.flatMap { param =>
      param._2.map(p => param._1 -> p)
    } ++ hdrs
    setHeaders(newHeaders: _*)
  }

  /**
   * Returns this request with the given query string parameters, discarding the existing ones.
   *
   * @param parameters the query string parameters
   */
  def setQueryString(parameters: (String, String)*): Self

  /**
   * Returns this request with the given query string parameters, preserving the existing ones.
   *
   * @param parameters the query string parameters
   */
  @deprecated("Use setQueryString or addQueryString", "1.0.0")
  def withQueryString(parameters: (String, String)*): Self = addQueryString(parameters: _*)

  /**
   * Returns this request with the given query string parameters, preserving the existing ones.
   *
   * @param parameters the query string parameters
   */
  def addQueryString(parameters: (String, String)*): Self = {
    val newQueryStringParams = queryString.toList.flatMap { param =>
      param._2.map(p => param._1 -> p)
    } ++ parameters
    setQueryString(newQueryStringParams: _*)
  }

  /**
   * Returns this request with the given query string parameters, discarding the existing ones.
   *
   * @param cookies the cookies to be used
   */
  def setCookies(cookies: WSCookie*): Self

  /**
   * Returns this request with the given query string parameters, preserving the existing ones.
   *
   * @param cookies the cookies to be used
   */
  def addCookies(cookies: WSCookie*): Self = {
    setCookies(this.cookies ++ cookies: _*)
  }

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
  def withRequestFilter(filter: WSRequestFilter): Self

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
   * Performs a PATCH request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PATCH request
   */
  def patch[T: BodyWritable](body: T): Future[Response]

  /**
   * Performs a PATCH request.
   *
   * @param body the file used as the payload body for this request
   * @return a future with the response for the PATCH request
   */
  def patch(body: File): Future[Response]

  /**
   * Performs a POST request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the POST request
   */
  def post[T: BodyWritable](body: T): Future[Response]

  /**
   * Performs a POST request.
   *
   * @param body the file used as the payload body for this request
   * @return a future with the response for the PATCH request
   */
  def post(body: File): Future[Response]

  /**
   * Performs a PUT request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PUT request
   */
  def put[T: BodyWritable](body: T): Future[Response]

  /**
   * Performs a PUT request.
   *
   * @param body the file used as the payload body for this request
   * @return a future with the response for the PUT request
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

  /**
   * Executes the given HTTP method.
   * @param method the HTTP method that will be executed
   * @return a future with the response for this request
   */
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
