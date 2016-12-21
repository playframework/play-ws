/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.stream.{javadsl, scaladsl}
import akka.util.ByteString

import java.{util => ju}

/**
 * A streamed response containing a response header and a streamable body.
 */
case class StreamedResponse(headers: WSResponseHeaders, body: scaladsl.Source[ByteString, _])
  extends play.libs.ws.StreamedResponse {


  override def getHeaders: play.libs.ws.WSResponseHeaders = {
    new play.libs.ws.DefaultWSResponseHeaders(headers.status, convert(headers.headers))
  }

  /**
   * Utility class for converting a Scala `Map` with a nested collection type into its idiomatic Java counterpart.
   * The reason why this source is written in Scala is that doing the conversion using Java is a lot more involved.
   * This utility class is used by `play.libs.ws.StreamedResponse`.
   */
  private def convert(headers: Map[String, Seq[String]]): ju.Map[String, ju.List[String]] = {
    import scala.collection.convert.WrapAsJava.{mapAsJavaMap, seqAsJavaList}
    mapAsJavaMap(headers.map { case (k, v) => k -> seqAsJavaList(v) })
  }

  override def getBody: javadsl.Source[ByteString, _] = body.asJava

}
