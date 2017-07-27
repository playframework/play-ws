/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import org.junit.Test;

import play.libs.ws.DefaultBodyReadables;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ByteStringRequestTest implements DefaultBodyReadables {

    @Test
    public void testGetBodyAsString() {
        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);
        when(ahcResponse.getContentType()).thenReturn(null);
        when(ahcResponse.getResponseBody()).thenReturn("wsBody");

        final String body = response.getBody();
        verify(ahcResponse, times(1)).getResponseBody();
        assertThat(body).isEqualTo("wsBody");
    }

    @Test
    public void testGetBodyAsBytes() {
        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);
        when(ahcResponse.getContentType()).thenReturn(null);
        when(ahcResponse.getResponseBodyAsBytes()).thenReturn("wsBody".getBytes());

        final String body = response.getBodyAsBytes().utf8String();
        verify(ahcResponse, times(1)).getResponseBodyAsBytes();
        assertThat(body).isEqualTo("wsBody");
    }

    @Test
    public void testGetBodyAsSource() throws ExecutionException, InterruptedException {
        final Response ahcResponse = mock(Response.class);
        final StandaloneAhcWSResponse response = new StandaloneAhcWSResponse(ahcResponse);
        when(ahcResponse.getContentType()).thenReturn(null);
        when(ahcResponse.getResponseBodyAsBytes()).thenReturn("wsBody".getBytes());

        final StringBuilder result = new StringBuilder();

        final ActorSystem system = ActorSystem.create("test-body-as-bytes");
        final Materializer materializer = ActorMaterializer.create(system);

        Source<String, ?> bodyAsSource = response.getBodyAsSource().map(ByteString::utf8String);
        Sink<String, CompletionStage<Done>> appender = Sink.foreach(result::append);

        // run and wait for completion
        bodyAsSource.runWith(appender, materializer).toCompletableFuture().get();

        verify(ahcResponse, times(1)).getResponseBodyAsBytes();
        assertThat(result.toString()).isEqualTo("wsBody");
    }
}
