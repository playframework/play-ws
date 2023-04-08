/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.util.ByteString;

/**
 * A body writable that takes a ByteString with InMemoryBody.
 *
 * @see ByteString
 */
public class InMemoryBodyWritable implements BodyWritable<ByteString> {
    private final InMemoryBody body;
    private final String contentType;

    public InMemoryBodyWritable(ByteString byteString, String contentType) {
        this.body = new InMemoryBody(byteString);
        this.contentType = contentType;
    }

    @Override
    public WSBody<ByteString> body() {
        return body;
    }

    @Override
    public String contentType() {
        return contentType;
    }
}

class InMemoryBody extends AbstractWSBody<ByteString> {
    InMemoryBody(ByteString body) {
        super(body);
    }
}
