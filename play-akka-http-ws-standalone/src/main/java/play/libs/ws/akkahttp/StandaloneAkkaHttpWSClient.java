/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.akkahttp;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.stream.Materializer;
import play.api.libs.ws.WSClientConfig;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;

import java.io.IOException;
import java.net.MalformedURLException;

public final class StandaloneAkkaHttpWSClient implements StandaloneWSClient {

  private final ActorSystem sys;
  private final Materializer mat;
  private final HttpsConnectionContext ctx;
  private final WSClientConfig config;

  public StandaloneAkkaHttpWSClient(ActorSystem sys, Materializer mat) {
    this(sys, mat, Http.get(sys).defaultClientHttpsContext(), WSClientConfig.forConfig());
  }

  public StandaloneAkkaHttpWSClient(ActorSystem sys, Materializer mat, HttpsConnectionContext ctx) {
    this(sys, mat, ctx, WSClientConfig.forConfig());
  }

  public StandaloneAkkaHttpWSClient(ActorSystem sys, Materializer mat, HttpsConnectionContext ctx, WSClientConfig config) {
    this.sys = sys;
    this.mat = mat;
    this.ctx = ctx;
    this.config = config;
  }

    /**
     * The underlying implementation of the client, if any.  You must cast the returned value to the type you want.
     *
     * @return the backing class.
     */
  @Override
  public Object getUnderlying() {
    return Http.get(sys);
  }

  /**
   * Returns a StandaloneWSRequest object representing the URL.  You can append additional
   * properties on the StandaloneWSRequest by chaining calls, and execute the request to
   * return an asynchronous {@code CompletionStage<StandaloneWSResponse>}.
   *
   * @param url the URL to request
   * @return the request
   */
  @Override
  public StandaloneWSRequest url(String url) {
    try {
      return new StandaloneAkkaHttpWSRequest(url, sys, mat, ctx, config);
    }
    catch (IllegalArgumentException ex) {
      throw new RuntimeException(new MalformedURLException(ex.getMessage()));
    }
  }

  /**
   * Closes this client, and releases underlying resources.
   * <p>
   * Use this for manually instantiated clients.
   */
  @Override
  public void close() throws IOException {
    Http.get(sys).shutdownAllConnectionPools();
  }
}
