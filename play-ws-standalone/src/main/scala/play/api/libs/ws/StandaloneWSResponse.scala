package play.api.libs.ws

import akka.util.ByteString
import play.api.libs.json.JsValue

import scala.xml.Elem

/**
 *
 */
trait StandaloneWSResponse {

  /**
   * Return the current headers of the request being constructed
   */
  def allHeaders: Map[String, Seq[String]]

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
   * Get a response header.
   */
  def header(key: String): Option[String]

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
