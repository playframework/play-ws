/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import java.net.URLEncoder.encode

import akka.util.ByteString
import play.api.libs.json.{ JsValue, Json }

import scala.xml.NodeSeq

/**
 * This trait defines several variants of withBody, so that XML, JSON and
 * other body types can be passed in directly to the request.
 */
trait WithBody { self: StandaloneWSRequest =>
  // TODO This could be a type class pattern.

  /**
   * Returns a request using an bytestring body, conditionally setting a content type of "application/octet-stream" if content type is not already set.
   *
   * @param byteString the byte string
   * @return the modified WSRequest.
   */
  def withBody(byteString: ByteString): Self = {
    withBodyAndContentType(InMemoryBody(byteString), "application/octet-stream")
  }

  /**
   * Returns a request using an XML body, optionally setting a content type of "text/plain" if content type is not already set.
   *
   * @param s the string body.
   * @return the modified WSRequest.
   */
  def withBody(s: String): Self = {
    withBodyAndContentType(InMemoryBody(ByteString.fromString(s)), "text/plain")
  }

  /**
   * Returns a request using an XML body, optionally setting a content type of "text/xml"  if content type is not already set.
   *
   * @param nodeSeq the XML nodes to set.
   * @return the modified WSRequest.
   */
  def withBody(nodeSeq: NodeSeq): Self = {
    // text/xml is fine to use as of RFC 7231 as there is no default charset any more.
    withBodyAndContentType(InMemoryBody(ByteString.fromString(nodeSeq.toString())), "text/xml")
  }

  /**
   * Returns a request using a JsValue body, optionally setting a content type of "application/json"  if content type is not already set.
   *
   * @param js the JsValue to set.
   * @return the modified WSRequest.
   */
  def withBody(js: JsValue): Self = {
    withBodyAndContentType(InMemoryBody(ByteString.fromString(Json.stringify(js))), "application/json")
  }

  /**
   * Returns a request using a map body, optionally setting a content type of "application/x-www-form-urlencoded"  if content type is not already set.
   *
   * @param formData the form data to set.
   * @return the modified WSRequest.
   */
  def withBody(formData: Map[String, Seq[String]]): Self = {
    val string = formData.flatMap(i => i._2.map(c => s"${i._1}=${encode(c, "UTF-8")}")).mkString("&")
    withBodyAndContentType(InMemoryBody(ByteString.fromString(string)), "application/x-www-form-urlencoded")
  }

  private def withBodyAndContentType(wsBody: WSBody, contentType: String): Self = {
    if (headers.contains("Content-Type")) {
      withBody(wsBody)
    } else {
      withBody(wsBody).withHeaders("Content-Type" -> contentType)
    }
  }
}
