/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
 * A WS HTTP response.
 */
trait StandaloneWSResponse {

  /**
   * Return the current headers for this response.
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
   * Get the underlying response object.
   */
  def underlying[T]: T

  /**
   * The response status code.
   */
  def status: Int

  /**
   * The response status message.
   */
  def statusText: String

  /**
   * Get all the cookies.
   */
  def cookies: Seq[WSCookie]

  /**
   * Get only one cookie, using the cookie name.
   */
  def cookie(name: String): Option[WSCookie]

  /**
   * @return the content type.
   */
  def contentType: String = header("Content-Type").getOrElse("application/octet-stream")

  /**
   * The response body as the given type.  This renders as the given type.
   * You must have a BodyReadable in implicit scope.
   *
   * The simplest use case is
   *
   * {{{
   * val responseBodyAsString: String = response.body[String]
   * }}}
   *
   * But you can also render as JSON
   *
   * {{{
   * val responseBodyAsJson: JsValue = response.getBody[JsValue]
   * }}}
   *
   * or as XML:
   *
   * {{{
   * val responseBodyAsByteString: ByteString = response.getBody[ByteString]
   * }}}
   */
  def body[T: BodyReadable]: T = {
    val readable = implicitly[BodyReadable[T]]
    readable.transform(this)
  }

  /**
   * @return the response body as String
   */
  def body: String

  /**
   * @return The response body as ByteString.
   */
  def bodyAsBytes: ByteString

  /**
   * @return the response as a source of bytes
   */
  def bodyAsSource: Source[ByteString, _]
}
