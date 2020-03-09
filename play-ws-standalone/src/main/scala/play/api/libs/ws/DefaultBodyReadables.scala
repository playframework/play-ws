/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.nio.ByteBuffer

import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
 * Defines common BodyReadable for a response backed by org.asynchttpclient.Response.
 */
trait DefaultBodyReadables {

  /**
   * Converts a response body into an akka.util.ByteString:
   *
   * {{{
   * val byteString = response.body[ByteString]
   * }}}
   */
  implicit val readableAsByteString: BodyReadable[ByteString] = BodyReadable(_.bodyAsBytes)

  /**
   * Converts a response body into a String.
   *
   * Note: this is only a best-guess effort and does not handle all content types. See
   * [[StandaloneWSResponse.body:String*]] for more information.
   *
   * {{{
   * val string = response.body[String]
   * }}}
   */
  implicit val readableAsString: BodyReadable[String] = BodyReadable(_.body)

  /**
   * Converts a response body into a read only ByteBuffer.
   *
   * {{{
   * val buffer = response.body[ByteBuffer]
   * }}}
   */
  implicit val readableAsByteBuffer: BodyReadable[ByteBuffer] = BodyReadable(_.bodyAsBytes.asByteBuffer)

  /**
   * Converts a response body into Array[Byte].
   *
   * {{{
   * val byteArray = response.body[Array[Byte]]
   * }}}
   */
  implicit val readableAsByteArray: BodyReadable[Array[Byte]] = BodyReadable(_.bodyAsBytes.toArray)

  /**
   * Converts a response body into Source[ByteString, NotUsed].
   */
  implicit val readableAsSource: BodyReadable[Source[ByteString, _]] = BodyReadable(_.bodyAsSource)
}

object DefaultBodyReadables extends DefaultBodyReadables
