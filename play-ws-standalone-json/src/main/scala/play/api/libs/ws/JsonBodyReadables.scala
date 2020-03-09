/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
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
    Json.parse(response.bodyAsBytes.toArray)
  }
}

object JsonBodyReadables extends JsonBodyReadables
