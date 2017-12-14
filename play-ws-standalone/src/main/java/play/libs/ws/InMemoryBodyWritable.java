/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.util.ByteString;

/**
 * A body writable that takes a bytestring with InMemoryBody.
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

    @Override
    public play.api.libs.ws.BodyWritable toScala() {
      return new play.api.libs.ws.BodyWritable((b) ->
        play.api.libs.ws.InMemoryBody$.MODULE$.apply(this.body.get()), this.contentType);
    }
}

class InMemoryBody extends AbstractWSBody<ByteString> {
    InMemoryBody(ByteString body) {
        super(body);
    }
}
