/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import play.libs.ws.*;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.io.*;
import java.nio.charset.StandardCharsets;

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

    @Test
    public void test_getBodyAsJsonWithoutCharset() throws IOException {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("test.json");
        InputStreamReader isr = new InputStreamReader(resourceAsStream, StandardCharsets.ISO_8859_1);
        String bodyString = new BufferedReader(isr).readLine();

        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);

        when(ahcResponse.getContentType()).thenReturn("application/json");
        when(ahcResponse.getResponseBody(StandardCharsets.UTF_8)).thenReturn(bodyString);

        JsonNode responseBody = response.getBody(JsonBodyReadables.instance.json());

        verify(ahcResponse, times(1)).getContentType();
        verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.UTF_8);
        assertThat(responseBody.toString()).isEqualTo(bodyString);
    }

    @Test
    public void test_getBodyAsJsonWithCharset() throws IOException {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("test.json");
        InputStreamReader isr = new InputStreamReader(resourceAsStream, StandardCharsets.ISO_8859_1);
        String bodyString = new BufferedReader(isr).readLine();

        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);

        when(ahcResponse.getContentType()).thenReturn("application/json;charset=iso-8859-1");
        when(ahcResponse.getResponseBody(StandardCharsets.ISO_8859_1)).thenReturn(bodyString);

        JsonNode responseBody = response.getBody(JsonBodyReadables.instance.json());

        verify(ahcResponse, times(1)).getContentType();
        verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.ISO_8859_1);
        assertThat(responseBody.toString()).isEqualTo(bodyString);
    }

}
