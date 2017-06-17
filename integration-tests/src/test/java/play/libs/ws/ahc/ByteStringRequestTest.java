/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import org.junit.Test;
import play.libs.ws.DefaultBodyReadables;
import play.libs.ws.DefaultBodyWritables;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 *
 */
public class ByteStringRequestTest implements DefaultBodyReadables {

    @Test
    public void testBody() {
        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);
        when(ahcResponse.getContentType()).thenReturn(null);
        when(ahcResponse.getResponseBody()).thenReturn("wsBody");

        final String body = response.getBody();
        verify(ahcResponse, times(1)).getResponseBody();
        assertThat(body).isEqualTo("wsBody");
    }

//
//        "get the getBody as UTF-8 by default when no content type" in {
//            val ahcResponse = mock[Response]
//            val response = new StandaloneAhcWSResponse(ahcResponse)
//            ahcResponse.getContentType returns null
//            ahcResponse.getResponseBody(any) returns "wsBody"
//
//            val body = response.getBody(string())
//            there was one(ahcResponse).getResponseBody(StandardCharsets.UTF_8)
//            body must be_==("wsBody")
//        }

//        "get the getBody as ISO_8859_1 by default when content type text/plain without charset" in {
//            val ahcResponse = mock[Response]
//            val response = new StandaloneAhcWSResponse(ahcResponse)
//            ahcResponse.getContentType returns "text/plain"
//            ahcResponse.getResponseBody(any) returns "wsBody"
//
//            val body = response.getBody(string())
//            there was one(ahcResponse).getResponseBody(StandardCharsets.ISO_8859_1)
//            body must be_==("wsBody")
//        }
//
//        "get the getBody as given charset when content type has explicit charset" in {
//            val ahcResponse = mock[Response]
//            val response = new StandaloneAhcWSResponse(ahcResponse)
//            ahcResponse.getContentType returns "text/plain; charset=UTF-16"
//            ahcResponse.getResponseBody(any) returns "wsBody"
//
//            val body = response.getBody(string())
//            there was one(ahcResponse).getResponseBody(StandardCharsets.UTF_16)
//            body must be_==("wsBody")
//        }

}
