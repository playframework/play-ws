/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
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
