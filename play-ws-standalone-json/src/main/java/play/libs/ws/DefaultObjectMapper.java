/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import play.api.libs.json.JsonConfig;
import play.api.libs.json.jackson.PlayJsonMapperModule;

/**
 *
 */
public class DefaultObjectMapper {

    public static final ObjectMapper instance = new ObjectMapper()
            .registerModule(new PlayJsonMapperModule(JsonConfig.apply()))
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

}
