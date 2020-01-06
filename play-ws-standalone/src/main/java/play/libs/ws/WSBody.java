/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.function.Supplier;

/**
 * A WS body marker interface.  The client is responsible for creating these instances:
 */
public interface WSBody<A> extends Supplier<A> {

}

abstract class AbstractWSBody<A> implements WSBody<A> {
    private final A body;

    AbstractWSBody(A body) {
        this.body = body;
    }

    public A get() {
        return body;
    }
}
