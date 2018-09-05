/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

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
   * Get the value of the header with the specified name. If there are more than one values
   * for this header, the first value is returned. If there are no values, than a None is
   * returned.
   *
   * @param name the header name
   * @return the header value
   */
  def header(name: String): Option[String] = headerValues(name).headOption

  /**
   * Get all the values of header with the specified name. If there are no values for
   * the header with the specified name, than an empty sequence is returned.
   *
   * @param name the header name.
   * @return all the values for this header name.
   */
  def headerValues(name: String): Seq[String] = headers.getOrElse(name, Seq.empty)

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
  def requestTimeout: Option[Duration]

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
  def withHttpHeaders(headers: (String, String)*): Self

  /**
   * Returns this request with the given headers, preserving the existing ones.
   *
   * @param hdrs the headers to be added
   */
  def addHttpHeaders(hdrs: (String, String)*): Self = {
    val newHeaders = headers.toList.flatMap { param =>
      param._2.map(p => param._1 -> p)
    } ++ hdrs
    withHttpHeaders(newHeaders: _*)
  }

  /**
   * Returns this request with the given query string parameters, discarding the existing ones.
   *
   * @param parameters the query string parameters
   */
  def withQueryStringParameters(parameters: (String, String)*): Self

  /**
   * Returns this request with the given query string parameters, preserving the existing ones.
   *
   * @param parameters the query string parameters
   */
  def addQueryStringParameters(parameters: (String, String)*): Self = {
    val newQueryStringParams = queryString.toList.flatMap { param =>
      param._2.map(p => param._1 -> p)
    } ++ parameters
    withQueryStringParameters(newQueryStringParams: _*)
  }

  /**
   * Returns this request with the given query string parameters, discarding the existing ones.
   *
   * @param cookies the cookies to be used
   */
  def withCookies(cookies: WSCookie*): Self

  /**
   * Returns this request with the given query string parameters, preserving the existing ones.
   *
   * @param cookies the cookies to be used
   */
  def addCookies(cookies: WSCookie*): Self = {
    withCookies(this.cookies ++ cookies: _*)
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
   * Sets the url for this request.
   */
  def withUrl(url: String): Self

  /**
   * Sets the method for this request
   */
  def withMethod(method: String): Self

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
   * Performs a POST request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the POST request
   */
  def post[T: BodyWritable](body: T): Future[Response]

  /**
   * Performs a PUT request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PUT request
   */
  def put[T: BodyWritable](body: T): Future[Response]

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
  def stream(): Future[Response]

}
