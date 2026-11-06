/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.core.JacksonException;

/**
 *
 */
public interface JsonBodyReadables {
    JsonBodyReadables instance = new JsonBodyReadables() {};

    default BodyReadable<JsonNode> json() {
        ObjectMapper defaultObjectMapper = DefaultObjectMapper.instance();
        return json(defaultObjectMapper);
    }

    default BodyReadable<JsonNode> json(ObjectMapper objectMapper) {
        return (response -> {
            // Jackson will automatically detect the correct encoding according to the rules in RFC-4627
            try {
                return objectMapper.readTree(response.getBody());
            } catch(JacksonException e) {
                throw new RuntimeException("Error parsing JSON from WS response wsBody", e);
            }
        });
    }

}
