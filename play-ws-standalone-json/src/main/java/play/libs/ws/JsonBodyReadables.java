/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 *
 */
public interface JsonBodyReadables {
    JsonBodyReadables instance = new JsonBodyReadables() {};

    default BodyReadable<JsonNode> json() {
        ObjectMapper defaultObjectMapper = DefaultObjectMapper.instance;
        return json(defaultObjectMapper);
    }

    default BodyReadable<JsonNode> json(ObjectMapper objectMapper) {
        return (response -> {
            // Jackson will automatically detect the correct encoding according to the rules in RFC-4627
            try {
                ByteString bodyAsBytes = response.getBodyAsBytes();
                return objectMapper.readValue(bodyAsBytes.toArray(), JsonNode.class);
            } catch(IOException e) {
                throw new RuntimeException("Error parsing JSON from WS response wsBody", e);
            }
        });
    }

}

