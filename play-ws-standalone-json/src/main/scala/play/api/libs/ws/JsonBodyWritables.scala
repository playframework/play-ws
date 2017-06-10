/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.util.ByteString
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import play.api.libs.json.{ JsValue, Json }

trait JsonBodyWritables {

  /**
   * Creates an InMemoryBody with "application/json" content type, using the static ObjectMapper.
   */
  implicit val writeableOf_JsValue: BodyWritable[JsValue] = {
    BodyWritable(a => InMemoryBody(ByteString.fromString(Json.stringify(a))), "application/json")
  }

  def body(objectMapper: ObjectMapper): BodyWritable[JsonNode] = BodyWritable(json =>
    InMemoryBody(ByteString.fromString(objectMapper.writer.writeValueAsString(json))), "application/json")
}

object JsonBodyWritables extends JsonBodyWritables
