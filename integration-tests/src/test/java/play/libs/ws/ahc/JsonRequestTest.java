/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import play.libs.ws.*;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames;
import play.shaded.ahc.org.asynchttpclient.Request;

import java.io.IOException;

import static org.mockito.Mockito.mock;

public class JsonRequestTest implements JsonBodyWritables {

    @Test
    public void setJson() throws IOException {
        JsonNode node = DefaultObjectMapper.instance.readTree("{\"k1\":\"v2\"}");

            StandaloneAhcWSClient client = mock(StandaloneAhcWSClient.class);
        StandaloneAhcWSRequest ahcWSRequest = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
                .setBody(body(node));

        Request req = ahcWSRequest.buildRequest();

        assertThat(req.getHeaders().get(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(node).isEqualTo(DefaultObjectMapper.instance.readTree("{\"k1\":\"v2\"}"));
    }

}
