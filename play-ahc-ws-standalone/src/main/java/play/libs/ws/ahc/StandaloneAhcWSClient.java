/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.StreamedResponse;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;

import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;

import java.io.IOException;

/**
 * A WS client backed by an AsyncHttpClient.
 * <p>
 * If you need to debug AHC, set org.asynchttpclient=DEBUG in your logging framework.
 */
public class StandaloneAhcWSClient implements StandaloneWSClient {

    private final AsyncHttpClient asyncHttpClient;
    private final Materializer materializer;

    public StandaloneAhcWSClient(AsyncHttpClient asyncHttpClient, Materializer materializer) {
        this.asyncHttpClient = asyncHttpClient;
        this.materializer = materializer;
    }

    @Override
    public Object getUnderlying() {
        return asyncHttpClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends StandaloneWSRequest, R extends StandaloneWSResponse, S extends StreamedResponse> StandaloneWSRequest<T, R, S> url(String url) {
        return (StandaloneWSRequest<T, R, S>) new StandaloneAhcWSRequest<T, R, S>(this, url, materializer);
    }

    @Override
    public void close() throws IOException {
        asyncHttpClient.close();
    }
}
