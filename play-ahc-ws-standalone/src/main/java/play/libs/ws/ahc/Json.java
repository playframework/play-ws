/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.api.libs.json.jackson.PlayJsonModule$;

/**
 * Helper functions to handle JsonNode values.
 */
public class Json {
    private static final ObjectMapper defaultObjectMapper =
        new ObjectMapper().registerModule(PlayJsonModule$.MODULE$);

    private static volatile ObjectMapper objectMapper = null;

    // Ensures that there always is *a* object mapper
    static ObjectMapper mapper() {
        if (objectMapper == null) {
            return defaultObjectMapper;
        } else {
            return objectMapper;
        }
    }

    /**
     * Parse a InputStream representing a json, and return it as a JsonNode.
     */
    public static JsonNode parse(java.io.InputStream src) {
        try {
            return mapper().readValue(src, JsonNode.class);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Inject the object mapper to use.
     *
     * This is intended to be used when Play starts up. 
     * By default, Play will inject its own object mapper here,
     * but this mapper can be overridden either by a custom plugin 
     * or from Global.onStart.
     */
    public static void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }

}
