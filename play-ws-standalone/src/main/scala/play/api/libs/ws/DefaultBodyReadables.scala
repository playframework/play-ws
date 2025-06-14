/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.nio.ByteBuffer

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

/**
 * Defines common BodyReadable for a response backed by `org.asynchttpclient.Response`.
 */
trait DefaultBodyReadables {

  /**
   * Converts a response body into an `org.apache.pekko.util.ByteString`:
   *
   * {{{
   * import org.apache.pekko.util.ByteString
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def example(response: play.api.libs.ws.StandaloneWSResponse): ByteString =
   *   response.body[ByteString]
   * }}}
   */
  implicit val readableAsByteString: BodyReadable[ByteString] = BodyReadable(_.bodyAsBytes)

  /**
   * Converts a response body into a `String`.
   *
   * Note: this is only a best-guess effort and does not handle all content types. See
   * [[StandaloneWSResponse.body:String*]] for more information.
   *
   * {{{
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def example(response: play.api.libs.ws.StandaloneWSResponse): String =
   *   response.body[String]
   * }}}
   */
  implicit val readableAsString: BodyReadable[String] = BodyReadable(_.body)

  /**
   * Converts a response body into a read only `ByteBuffer`.
   *
   * {{{
   * import java.nio.ByteBuffer
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def example(response: play.api.libs.ws.StandaloneWSResponse): ByteBuffer =
   *   response.body[ByteBuffer]
   * }}}
   */
  implicit val readableAsByteBuffer: BodyReadable[ByteBuffer] = BodyReadable(_.bodyAsBytes.asByteBuffer)

  /**
   * Converts a response body into `Array[Byte]`.
   *
   * {{{
   * import play.api.libs.ws.DefaultBodyReadables._
   *
   * def example(response: play.api.libs.ws.StandaloneWSResponse): Array[Byte] =
   *   response.body[Array[Byte]]
   * }}}
   */
  implicit val readableAsByteArray: BodyReadable[Array[Byte]] = BodyReadable(_.bodyAsBytes.toArray)

  /**
   * Converts a response body into `Source[ByteString, _]`.
   */
  implicit val readableAsSource: BodyReadable[Source[ByteString, ?]] = BodyReadable(_.bodyAsSource)
}

object DefaultBodyReadables extends DefaultBodyReadables
