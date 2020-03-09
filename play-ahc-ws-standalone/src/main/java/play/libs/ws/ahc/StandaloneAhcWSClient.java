/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.Done;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import com.typesafe.sslconfig.ssl.SystemConfiguration;
import com.typesafe.sslconfig.ssl.debug.DebugConfiguration;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import play.api.libs.ws.ahc.*;
import play.api.libs.ws.ahc.cache.AhcHttpCache;
import play.api.libs.ws.ahc.cache.CachingAsyncHttpClient;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSResponse;
import play.shaded.ahc.org.asynchttpclient.AsyncCompletionHandler;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.*;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.FutureConverters$;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * A WS asyncHttpClient backed by an AsyncHttpClient instance.
 */
public class StandaloneAhcWSClient implements StandaloneWSClient {

    private final AsyncHttpClient asyncHttpClient;
    private final Materializer materializer;

    /**
     * Creates a new client.
     *
     * @param asyncHttpClient the underlying AsyncHttpClient
     * @param materializer    the Materializer to use for streams
     */
    @Inject
    public StandaloneAhcWSClient(AsyncHttpClient asyncHttpClient, Materializer materializer) {
        this.asyncHttpClient = asyncHttpClient;
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

        AsyncCompletionHandler<Response> handler = new ResponseAsyncCompletionHandler(scalaPromise);

        try {
            asyncHttpClient.executeRequest(request, handler);
        } catch (RuntimeException exception) {
            scalaPromise.failure(exception);
        }
        Future<StandaloneWSResponse> future = scalaPromise.future();
        return FutureConverters.toJava(future);
    }

    CompletionStage<StandaloneWSResponse> executeStream(Request request, ExecutionContext ec) {
        final Promise<StandaloneWSResponse> streamStarted = scala.concurrent.Promise$.MODULE$.apply();
        final Promise<Done> streamCompletion = scala.concurrent.Promise$.MODULE$.apply();

        Function<StreamedState, StandaloneWSResponse> f = state -> {
            Publisher<HttpResponseBodyPart> publisher = state.publisher();
            Publisher<HttpResponseBodyPart> wrap = new Publisher<HttpResponseBodyPart>() {
                @Override
                public void subscribe(Subscriber<? super HttpResponseBodyPart> s) {
                    publisher.subscribe(
                        new Subscriber<HttpResponseBodyPart>() {
                            @Override
                            public void onSubscribe(Subscription sub) {
                                s.onSubscribe(sub);
                            }

                            @Override
                            public void onNext(HttpResponseBodyPart httpResponseBodyPart) {
                                s.onNext(httpResponseBodyPart);
                            }

                            @Override
                            public void onError(Throwable t) {
                                s.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                FutureConverters$.MODULE$.toJava(streamCompletion.future())
                                    .handle((d, t) -> {
                                        if (d != null) s.onComplete();
                                        else s.onError(t);
                                        return null;
                                    });
                            }
                        }
                    );
                }
            };

            return new StreamedResponse(this,
                state.statusCode(),
                state.statusText(),
                state.uriOption().get(),
                state.responseHeaders(),
                wrap,
                asyncHttpClient.getConfig().isUseLaxCookieEncoder());
        };

        asyncHttpClient.executeRequest(request, new DefaultStreamedAsyncHandler<>(f,
            streamStarted,
            streamCompletion
        ));
        return FutureConverters.toJava(streamStarted.future());
    }

    /**
     * A convenience method for creating a StandaloneAhcWSClient from configuration.
     *
     * @param ahcWSClientConfig the configuration object
     * @param materializer      an akka materializer
     * @return a fully configured StandaloneAhcWSClient instance.
     * @see #create(AhcWSClientConfig, AhcHttpCache, Materializer)
     */
    public static StandaloneAhcWSClient create(AhcWSClientConfig ahcWSClientConfig, Materializer materializer) {
        return create(
            ahcWSClientConfig,
            null /* no cache*/,
            materializer
        );
    }

    /**
     * A convenience method for creating a StandaloneAhcWSClient from configuration.
     *
     * @param ahcWSClientConfig the configuration object
     * @param cache             if not null, will be used for HTTP response caching.
     * @param materializer      an akka materializer
     * @return a fully configured StandaloneAhcWSClient instance.
     */
    public static StandaloneAhcWSClient create(AhcWSClientConfig ahcWSClientConfig, AhcHttpCache cache, Materializer materializer) {
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
        DefaultAsyncHttpClient defaultAsyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);

        AsyncHttpClient ahcClient;
        if (cache != null) {
            ahcClient = new CachingAsyncHttpClient(defaultAsyncHttpClient, cache);
        } else {
            ahcClient = defaultAsyncHttpClient;
        }
        return new StandaloneAhcWSClient(ahcClient, materializer);
    }

    ByteString blockingToByteString(Source<ByteString, ?> bodyAsSource) {
        try {
            return bodyAsSource
                .runFold(ByteString.createBuilder(), ByteStringBuilder::append, materializer)
                .thenApply(ByteStringBuilder::result)
                .toCompletableFuture()
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ResponseAsyncCompletionHandler extends AsyncCompletionHandler<Response> {
        private final Promise<StandaloneWSResponse> scalaPromise;

        public ResponseAsyncCompletionHandler(Promise<StandaloneWSResponse> scalaPromise) {
            this.scalaPromise = scalaPromise;
        }

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
    }
}
