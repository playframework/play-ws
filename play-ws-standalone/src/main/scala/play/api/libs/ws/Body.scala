/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.annotation.implicitNotFound

/**
 * A body for the request.
 */
sealed trait WSBody

case object EmptyBody extends WSBody

/**
 * An in memory body
 *
 * @param bytes The bytes of the body
 */
case class InMemoryBody(bytes: ByteString) extends WSBody

/**
 * A body containing a source of bytes
 *
 * @param source A flow of the bytes of the body
 */
case class SourceBody(source: Source[ByteString, _]) extends WSBody

@implicitNotFound(
  "Cannot find an instance of StandaloneWSResponse to ${R}. Define a BodyReadable[${R}] or extend play.api.libs.ws.ahc.DefaultBodyReadables")
class BodyReadable[+R](val transform: StandaloneWSResponse => R)

object BodyReadable {
  def apply[R](transform: StandaloneWSResponse => R): BodyReadable[R] = new BodyReadable[R](transform)
}

/**
 * This is a type class pattern for writing different types of bodies to a WS request.
 */
@implicitNotFound(
  "Cannot find an instance of ${A} to WSBody. Define a BodyWritable[${A}] or extend play.api.libs.ws.ahc.DefaultBodyWritables")
class BodyWritable[-A](val transform: A => WSBody, val contentType: String) {
  def map[B](f: B => A): BodyWritable[B] = new BodyWritable(b => transform(f(b)), contentType)
}

object BodyWritable {
  def apply[A](transform: (A => WSBody), contentType: String): BodyWritable[A] =
    new BodyWritable(transform, contentType)
}

