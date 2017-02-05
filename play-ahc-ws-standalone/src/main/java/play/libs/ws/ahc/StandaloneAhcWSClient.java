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
import play.api.libs.ws.ahc.cache.AhcHttpCache;
import play.api.libs.ws.ahc.cache.ResponseEntry;
import play.api.libs.ws.ahc.cache.EffectiveURIKey;
import play.api.libs.ws.ahc.cache.CachingAsyncHttpClient;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSResponse;
import play.shaded.ahc.org.asynchttpclient.*;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;

import javax.cache.Cache;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

/**
 * A WS asyncHttpClient backed by an AsyncHttpClient instance.
 */
public class StandaloneAhcWSClient implements StandaloneWSClient {

    private final AsyncHttpClient asyncHttpClient;
    private final Materializer materializer;

    @Inject
    public StandaloneAhcWSClient(AsyncHttpClient asyncHttpClient, Cache<EffectiveURIKey, ResponseEntry> cache, Materializer materializer) {
        if (cache != null) {
            AhcHttpCache httpCache = new AhcHttpCache(cache);
            this.asyncHttpClient = new CachingAsyncHttpClient(asyncHttpClient, httpCache);
        } else {
            this.asyncHttpClient = asyncHttpClient;
        }
        this.materializer = materializer;
    }

    @Override
    public Object getUnderlying() {
        return asyncHttpClient;
    }

    @Override
    public StandaloneAhcWSRequest url(String url) {
        return new StandaloneAhcWSRequest(this, url, materializer);
    }

    @Override
    public void close() throws IOException {
        asyncHttpClient.close();
    }

    CompletionStage<StandaloneWSResponse> execute(Request request) {
        final Promise<StandaloneWSResponse> scalaPromise = scala.concurrent.Promise$.MODULE$.apply();

        AsyncCompletionHandler<Response> handler = new AsyncCompletionHandler<Response>() {
            @Override
            public Response onCompleted(Response response) {
                StandaloneAhcWSResponse r = new StandaloneAhcWSResponse(response);
                scalaPromise.success(r);
                return response;
            }

            @Override
            public void onThrowable(Throwable t) {
                scalaPromise.failure(t);
            }
        };

        try {
            asyncHttpClient.executeRequest(request, handler);
        } catch (RuntimeException exception) {
            scalaPromise.failure(exception);
        }
        Future<StandaloneWSResponse> future = scalaPromise.future();
        return FutureConverters.toJava(future);
    }

    /**
     * A convenience method for creating a StandaloneAhcWSClient from configuration.
     *
     * @param ahcWSClientConfig the configuration object
     * @param cache if not null, will be used for HTTP response caching.
     * @param materializer an akka materializer
     * @return a fully configured StandaloneAhcWSClient instance.
     */
    public static StandaloneAhcWSClient create(AhcWSClientConfig ahcWSClientConfig, Cache<EffectiveURIKey, ResponseEntry> cache, Materializer materializer) {
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

        // Create the AHC asyncHttpClient
        DefaultAsyncHttpClientConfig asyncHttpClientConfig = ahcBuilder.build();
        DefaultAsyncHttpClient ahcClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);

        return new StandaloneAhcWSClient(ahcClient, cache, materializer);
    }

    ExecutionContext executionContext() {
        return materializer.executionContext();
    }
}
