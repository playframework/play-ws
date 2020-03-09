/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.io.IOException;

/**
 * This is the WS Client interface.
 */
public interface StandaloneWSClient extends java.io.Closeable {

    /**
     * The underlying implementation of the client, if any.  You must cast the returned value to the type you want.
     *
     * @return the backing class.
     */
    Object getUnderlying();

    /**
     * Returns a StandaloneWSRequest object representing the URL.  You can append additional
     * properties on the StandaloneWSRequest by chaining calls, and execute the request to
     * return an asynchronous {@code CompletionStage<StandaloneWSResponse>}.
     *
     * @param url the URL to request
     * @return the request
     */
    StandaloneWSRequest url(String url);

    /**
     * Closes this client, and releases underlying resources.
     * <p>
     * Use this for manually instantiated clients.
     */
    void close() throws IOException;
}
