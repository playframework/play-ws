/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.stream.javadsl
import akka.stream.scaladsl
import akka.util.ByteString

/**
 * A streamed response containing a response header and a streamable body.
 */
case class StreamedResponse(headers: WSResponseHeaders, body: scaladsl.Source[ByteString, _])
    extends play.libs.ws.StreamedResponse {

  override def getHeaders: play.libs.ws.WSResponseHeaders = {
    new play.libs.ws.DefaultWSResponseHeaders(headers.status, CollectionUtil.convert(headers.headers))
  }
  override def getBody: javadsl.Source[ByteString, _] = body.asJava

}
