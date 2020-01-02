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
   * import play.api.libs.ws.StandaloneWSResponse
   * import play.api.libs.ws.JsonBodyReadables._
   *
   * def json(r: StandaloneWSResponse) = r.body[play.api.libs.json.JsValue]
   * }}}
   */
  implicit val readableAsJson: BodyReadable[JsValue] = BodyReadable { response =>
    Json.parse(response.body)
  }
}

object JsonBodyReadables extends JsonBodyReadables
