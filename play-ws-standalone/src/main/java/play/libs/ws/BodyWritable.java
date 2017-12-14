/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import java.util.function.Supplier;

/**
 * Writes out a {@code WSBody<A>}
 *
 * @param <A> the type of body.
 */
public interface BodyWritable<A> {
    WSBody<A> body();

    String contentType();


}


