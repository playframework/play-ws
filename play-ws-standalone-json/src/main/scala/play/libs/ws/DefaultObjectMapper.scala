/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws

import com.fasterxml.jackson.databind.ObjectMapper

object DefaultObjectMapper {
  def instance(): ObjectMapper = play.api.libs.json.jackson.JacksonJson.get.mapper
}
