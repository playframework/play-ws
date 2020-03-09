/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

/**
 * Writes out a {@code WSBody<A>}
 *
 * @param <A> the type of body.
 */
public interface BodyWritable<A> {
    WSBody<A> body();

    String contentType();
}


