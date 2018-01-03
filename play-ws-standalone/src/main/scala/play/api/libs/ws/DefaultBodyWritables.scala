/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.io.File
import java.nio.ByteBuffer
import java.util.function.Supplier

import akka.stream.scaladsl.StreamConverters.fromInputStream
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.ByteString

import scala.compat.java8.FunctionConverters.asScalaFromSupplier

/**
 * Default BodyWritable for a request body, for use with
 * requests that take a body such as PUT, POST and PATCH.
 *
 * {{{
 * class MyClass extends DefaultBodyWritables {
 *   def postBody() = {
 *     val getBody: String = ...
 *     ws.url(...).post(getBody).map { response => ... }
 *   }
 * }
 * }}}
 */
trait DefaultBodyWritables {

  /**
   * Creates an SourceBody with "application/octet-stream" content type from a file.
   */
  implicit val writableOf_File: BodyWritable[File] = {
    BodyWritable(file => SourceBody(FileIO.fromPath(file.toPath)), "application/octet-stream")
  }

  /**
   * Creates an SourceBody with "application/octet-stream" content type from an inputstream.
   */
  implicit val writableOf_InputStream: BodyWritable[Supplier[java.io.InputStream]] = {
    BodyWritable(supplier => SourceBody(fromInputStream(asScalaFromSupplier(supplier))), "application/octet-stream")
  }

  /**
   * Creates an SourceBody with "application/octet-stream" content type from a file.
   */
  implicit val writableOf_Source: BodyWritable[Source[ByteString, _]] = {
    BodyWritable(source => SourceBody(source), "application/octet-stream")
  }

  /**
   * Creates an InMemoryBody with "text/plain" content type.
   */
  implicit val writeableOf_String: BodyWritable[String] = {
    BodyWritable(str => InMemoryBody(ByteString.fromString(str)), "text/plain")
  }

  /**
   * Creates an InMemoryBody with "text/plain" content type from a StringBuilder
   */
  implicit val writeableOf_StringBuilder: BodyWritable[StringBuilder] = {
    BodyWritable(str => InMemoryBody(ByteString.fromString(str.toString())), "text/plain")
  }

  /**
   * Creates an InMemoryBody with "application/octet-stream" content type from an array of bytes.
   */
  implicit val writeableOf_ByteArray: BodyWritable[Array[Byte]] = {
    BodyWritable(bytes => InMemoryBody(ByteString(bytes)), "application/octet-stream")
  }

  /**
   * Creates an InMemoryBody with "application/octet-stream" content type from a bytebuffer.
   */
  implicit val writeableOf_ByteBuffer: BodyWritable[ByteBuffer] = {
    BodyWritable(buffer => InMemoryBody(ByteString.fromByteBuffer(buffer)), "application/octet-stream")
  }

  /**
   * Creates an InMemoryBody with "application/octet-stream" content type.
   */
  implicit val writeableOf_Bytes: BodyWritable[ByteString] = {
    BodyWritable(byteString => InMemoryBody(byteString), "application/octet-stream")
  }

  /**
   * Creates a BodyWritable with an identity function, with "application/octet-stream" content type.
   */
  implicit val writeableOf_WsBody: BodyWritable[WSBody] = {
    BodyWritable(identity, "application/octet-stream")
  }

  /**
   * Creates an InMemoryBody with "application/x-www-form-urlencoded" content type.
   */
  implicit val writeableOf_urlEncodedForm: BodyWritable[Map[String, Seq[String]]] = {
    import java.net.URLEncoder
    BodyWritable(
      formData =>
        InMemoryBody(ByteString.fromString(formData.flatMap(item => item._2.map(c => s"${item._1}=${URLEncoder.encode(c, "UTF-8")}")).mkString("&"))),
      "application/x-www-form-urlencoded")
  }

  implicit val writeableOf_urlEncodedSimpleForm: BodyWritable[Map[String, String]] = {
    writeableOf_urlEncodedForm.map[Map[String, String]](_.mapValues(v => Seq(v)))
  }

}

object DefaultBodyWritables extends DefaultBodyWritables
