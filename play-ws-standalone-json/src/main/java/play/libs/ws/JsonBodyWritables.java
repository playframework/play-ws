/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.apache.pekko.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JsonBodyWritables {

    JsonBodyWritables instance = new JsonBodyWritables() {};

    /**
     * Creates a {@link InMemoryBodyWritable} for JSON, setting the content-type to "application/json", using the
     * default object mapper.
     *
     * @param node the node to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(JsonNode node) {
        return body(node, DefaultObjectMapper.instance);
    }

    /**
     * Creates a {@link InMemoryBodyWritable} for JSON, setting the content-type to "application/json".
     *
     * @param node the node to pass in.
     * @param objectMapper the object mapper to create a JSON document.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(JsonNode node, ObjectMapper objectMapper) {
        try {
            Object json = objectMapper.readValue(node.toString(), Object.class);
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json);
            return new InMemoryBodyWritable(ByteString.fromArrayUnsafe(bytes), "application/json");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
