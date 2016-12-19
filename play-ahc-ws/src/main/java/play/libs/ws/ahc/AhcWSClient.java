/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

import javax.inject.Inject;
import java.io.IOException;

/**
 * A WS client backed by an AsyncHttpClient.
 *
 * If you need to debug AHC, set org.asynchttpclient=DEBUG in your logging framework.
 */
public class AhcWSClient implements WSClient {

    private final StandaloneAhcWSClient client;

    @Inject
    public AhcWSClient(AsyncHttpClient asyncHttpClient, Materializer materializer) {
        this.client = new StandaloneAhcWSClient(asyncHttpClient, materializer);
    }

    @Inject
    public AhcWSClient(StandaloneAhcWSClient client) {
        this.client = client;
    }

    @Override
    public Object getUnderlying() {
        return client.getUnderlying();
    }

    @Override
    public WSRequest url(String url) {
        final StandaloneAhcWSRequest plainWSRequest = (StandaloneAhcWSRequest) client.url(url);
        return new AhcWSRequest(this, plainWSRequest);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
