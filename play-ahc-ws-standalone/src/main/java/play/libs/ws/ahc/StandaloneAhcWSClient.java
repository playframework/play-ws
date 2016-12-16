/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;

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
    public StandaloneWSRequest url(String url) {
        return new StandaloneAhcWSRequest(this, url, materializer);
    }

    @Override
    public void close() throws IOException {
        asyncHttpClient.close();
    }
}
