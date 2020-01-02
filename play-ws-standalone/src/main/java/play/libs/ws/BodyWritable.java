/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
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


