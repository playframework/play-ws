/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.json

import com.fasterxml.jackson.databind.ObjectMapper

object BadCitizen {
  def mapper(): ObjectMapper = play.api.libs.json.jackson.JacksonJson.get.mapper
}
