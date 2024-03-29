/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

/**
 *
 */
public class SourceBodyWritable implements BodyWritable<Source<ByteString, ?>> {
    private final SourceBody body;
    private final String contentType;

    /**
     * A SourceBody with a content type
     * @param body a source of bytestring
     * @param contentType the content type
     */
    public SourceBodyWritable(Source<ByteString, ?> body, String contentType) {
        this.body = new SourceBody(body);
        this.contentType = contentType;
    }

    /**
     * A SourceBody with a content type of "application/octet-stream"
     * @param body a source of bytestring.
     */
    public SourceBodyWritable(Source<ByteString, ?> body) {
        this.body = new SourceBody(body);
        this.contentType = "application/octet-stream";
    }

    @Override
    public WSBody<Source<ByteString, ?>> body() {
        return body;
    }

    @Override
    public String contentType() {
        return contentType;
    }
}

class SourceBody extends AbstractWSBody<Source<ByteString, ?>> {
    SourceBody(Source<ByteString, ?> body) {
        super(body);
    }
}
