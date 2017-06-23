/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

/**
 * Provides implicit for converting a response to JsValue.
 *
 * See https://github.com/playframework/play-json for details of Play-JSON.
 */
trait JsonBodyReadables {

  import play.api.libs.json._

  /**
   * Converts a response body into Play JSON format:
   *
   * {{{
   * val json = response.body[play.api.libs.json.JsValue]
   * }}}
   */
  implicit val readableAsJson: BodyReadable[JsValue] = BodyReadable { response =>
    val body = response.bodyAsBytes
    Json.parse(body.decodeString(detectEncoding(body)))
  }

  // Leverage Jackson's RFC-4627 / 7159 JSON encoding support.
  private[ws] def detectEncoding(in: akka.util.ByteString): String = {
    // https://github.com/FasterXML/jackson-core/blob/master/src/main/java/com/fasterxml/jackson/core/json/ByteSourceJsonBootstrapper.java
    // Also see https://stackoverflow.com/a/38036753 if this is insufficient
    import com.fasterxml.jackson.core.io.IOContext
    import com.fasterxml.jackson.core.json.ByteSourceJsonBootstrapper
    import com.fasterxml.jackson.core.util.BufferRecycler
    val ctx = new IOContext(new BufferRecycler, null, false)
    val strapper = new ByteSourceJsonBootstrapper(ctx, in.toArray, 0, 4)
    strapper.detectEncoding.getJavaName
  }
}

object JsonBodyReadables extends JsonBodyReadables
