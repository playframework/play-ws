/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.util.ByteString
import play.api.libs.json._
import scala.annotation._

/**
 * This is a type class pattern for writing different types of bodies to a WS request.
 */
@implicitNotFound(
  "Cannot find an instance of ${A} to ByteString. Try to define a BodyWritable[${A}]"
)
class BodyWritable[-A](val transform: A => ByteString, val contentType: String) {
  def map[B](f: B => A): BodyWritable[B] = new BodyWritable(b => transform(f(b)), contentType)
}

object BodyWritable extends DefaultBodyWritables {
  def apply[A](transform: (A => ByteString), contentType: String): BodyWritable[A] =
    new BodyWritable(transform, contentType)
}

/**
 * Default Writeable.
 */
trait DefaultBodyWritables {

  /**
   * `Writeable` for `NodeSeq` values - literal Scala XML.
   */
  implicit def writeableOf_NodeSeq[C <: scala.xml.NodeSeq]: BodyWritable[C] = {
    BodyWritable(xml => ByteString.fromString(xml.toString()), "text/xml")
  }

  /**
   * `Writeable` for `NodeBuffer` values - literal Scala XML.
   */
  implicit val writeableOf_NodeBuffer: BodyWritable[scala.xml.NodeBuffer] = {
    BodyWritable(xml => ByteString.fromString(xml.toString()), "text/xml")
  }

  /**
   * `Writeable` for `urlEncodedForm` values
   */
  implicit val writeableOf_urlEncodedForm: BodyWritable[Map[String, Seq[String]]] = {
    import java.net.URLEncoder
    BodyWritable(
      formData =>
        ByteString.fromString(formData.flatMap(item => item._2.map(c => s"${item._1}=${URLEncoder.encode(c, "UTF-8")}")).mkString("&")),
      "application/x-www-form-urlencoded"
    )
  }

  /**
   * `Writeable` for `JsValue` values - Json
   */
  implicit val writeableOf_JsValue: BodyWritable[JsValue] = {
    BodyWritable(a => ByteString.fromString(Json.stringify(a)), "application/json")
  }

  /**
   * Straightforward `Writeable` for String values.
   */
  implicit val wString: BodyWritable[String] = BodyWritable[String](str => ByteString.fromString(str), "text/plain")

  /**
   * Straightforward `Writeable` for Array[Byte] values.
   */
  implicit val wByteArray: BodyWritable[Array[Byte]] = BodyWritable(bytes => ByteString(bytes), "application/octet-stream")

  /**
   * Straightforward `Writeable` for ByteString values.
   */
  implicit val wBytes: BodyWritable[ByteString] = BodyWritable(identity, "application/octet-stream")

}