/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

/**
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
    Json.parse(response.body)
  }
}

object JsonBodyReadables extends JsonBodyReadables
