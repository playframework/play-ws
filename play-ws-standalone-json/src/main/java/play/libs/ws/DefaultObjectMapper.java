/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import play.api.libs.json.JsonParserSettings;
import play.api.libs.json.jackson.PlayJsonModule;

/**
 *
 */
public class DefaultObjectMapper {

    public static final ObjectMapper instance = new ObjectMapper()
            .registerModule(new PlayJsonModule(JsonParserSettings.apply()))
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

}
