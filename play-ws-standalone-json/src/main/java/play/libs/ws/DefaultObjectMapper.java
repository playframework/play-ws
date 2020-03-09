/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import play.api.libs.json.jackson.PlayJsonModule$;

/**
 *
 */
public class DefaultObjectMapper {

    public static final ObjectMapper instance = new ObjectMapper()
            .registerModule(PlayJsonModule$.MODULE$)
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

}
