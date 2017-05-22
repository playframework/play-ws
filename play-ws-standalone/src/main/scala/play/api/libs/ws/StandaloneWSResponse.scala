/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.util.ByteString
import play.api.libs.json.JsValue

import scala.xml.Elem

/**
 * A WS HTTP response.
 */
trait StandaloneWSResponse {

  /**
   * Return the current headers for this response.
   */
  @deprecated("Use headers instead", "1.0.0")
  def allHeaders: Map[String, Seq[String]] = headers

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
   * The response body as String.
   */
  def body: String

  /**
   * The response body as Xml.
   */
  def xml: Elem

  /**
   * The response body as Json.
   */
  def json: JsValue

  /**
   * The response body as a byte string.
   */
  def bodyAsBytes: ByteString
}
