/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import com.typesafe.sslconfig.ssl.SystemConfiguration;
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration;
import org.slf4j.LoggerFactory;
import play.api.libs.ws.ahc.AhcConfigBuilder;
import play.api.libs.ws.ahc.AhcLoggerFactory;
import play.api.libs.ws.ahc.AhcWSClientConfig;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.StreamedResponse;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;

import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;

import java.io.IOException;

/**
 * A WS client backed by an AsyncHttpClient instance.
 *
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

    /**
     * A convenience method for creating a StandaloneAhcWSClient from configuration.
     *
     * @param ahcWSClientConfig the configuration object
     * @param materializer an akka materializer
     * @return a fully configured StandaloneAhcWSClient instance.
     */
    public static StandaloneAhcWSClient create(AhcWSClientConfig ahcWSClientConfig, Materializer materializer) {
        AhcLoggerFactory loggerFactory = new AhcLoggerFactory(LoggerFactory.getILoggerFactory());

        // Set up debugging configuration
        if (ahcWSClientConfig.wsClientConfig().ssl().debug().enabled()) {
            new DebugConfiguration(loggerFactory).configure(ahcWSClientConfig.wsClientConfig().ssl().debug());
        }

        // Configure the AsyncHttpClientConfig.Builder from the application.conf file...
        final AhcConfigBuilder builder = new AhcConfigBuilder(ahcWSClientConfig);
        final DefaultAsyncHttpClientConfig.Builder ahcBuilder = builder.configure();

        // Set up SSL configuration settings that are global..
        new SystemConfiguration(loggerFactory).configure(ahcWSClientConfig.wsClientConfig().ssl());

        // Create the AHC client
        DefaultAsyncHttpClientConfig asyncHttpClientConfig = ahcBuilder.build();
        DefaultAsyncHttpClient ahcClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);

        return new StandaloneAhcWSClient(ahcClient, materializer);
    }
}
