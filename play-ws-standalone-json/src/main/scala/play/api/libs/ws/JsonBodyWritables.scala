/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.util.ByteString
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import play.api.libs.json.JsValue
import play.api.libs.json.Json

trait JsonBodyWritables {

  /**
   * Creates an InMemoryBody with "application/json" content type, using the static ObjectMapper.
   */
  implicit val writeableOf_JsValue: BodyWritable[JsValue] = {
    BodyWritable(a => InMemoryBody(ByteString.fromArrayUnsafe(Json.toBytes(a))), "application/json")
  }

  def body(objectMapper: ObjectMapper): BodyWritable[JsonNode] =
    BodyWritable(
      json => InMemoryBody(ByteString.fromArrayUnsafe(objectMapper.writer.writeValueAsBytes(json))),
      "application/json"
    )
}

object JsonBodyWritables extends JsonBodyWritables
