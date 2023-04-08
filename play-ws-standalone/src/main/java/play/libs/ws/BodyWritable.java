/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
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
