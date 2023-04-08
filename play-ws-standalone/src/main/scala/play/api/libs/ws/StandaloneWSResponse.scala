/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
 * A WS HTTP response.
 */
trait StandaloneWSResponse {

  /**
   * Returns the URI for this response, which can differ from the request one
   * in case of redirection.
   */
  def uri: java.net.URI

  /**
   * Returns the current headers for this response.
   */
  def headers: Map[String, scala.collection.Seq[String]]

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
  def headerValues(name: String): scala.collection.Seq[String] = headers.getOrElse(name, Seq.empty)

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
  def cookies: scala.collection.Seq[WSCookie]

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
   * import play.api.libs.ws.StandaloneWSResponse
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def responseBodyAsString(response: StandaloneWSResponse): String =
   *   response.body[String]
   * }}}
   *
   * But you can also render as JSON
   *
   * {{{
   * // not compilable: requires `play-ws-standalone-json` dependency
   * import play.api.libs.json.JsValue
   * import play.api.libs.ws.StandaloneWSResponse
   *
   * def responseBodyAsJson(response: StandaloneWSResponse): JsValue =
   *   response.body[JsValue]
   * }}}
   *
   * or as binary:
   *
   * {{{
   * import akka.util.ByteString
   * import play.api.libs.ws.StandaloneWSResponse
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def responseBodyAsByteString(response: StandaloneWSResponse): ByteString =
   *   response.body[ByteString]
   * }}}
   */
  def body[T: BodyReadable]: T = {
    val readable = implicitly[BodyReadable[T]]
    readable.transform(this)
  }

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
