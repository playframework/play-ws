/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * This interface defines a set of reads for converting a response
 * into a readable format.
 */
public interface DefaultBodyReadables {

    /**
     * Converts a response body into an org.apache.pekko.util.ByteString:
     *
     * {{{
     * ByteString byteString = response.body(byteString())
     * }}}
     */
    default BodyReadable<ByteString> byteString() {
        return StandaloneWSResponse::getBodyAsBytes;
    }

    /**
     * Converts a response body into a String.
     *
     * Note: this is only a best-guess effort and does not handle all content types. See
     * {@link StandaloneWSResponse#getBody()} for more information.
     *
     * {{{
     * String string = response.body(string())
     * }}}
     */
    default BodyReadable<String> string() {
        return StandaloneWSResponse::getBody;
    }

    /**
     * Converts a response body into ByteBuffer.
     *
     * {{{
     * ByteBuffer buffer = response.body(byteBuffer())
     * }}}
     */
    default BodyReadable<ByteBuffer> byteBuffer() {
        // toByteBuffer returns a copy of the bytes
        return response -> response.getBodyAsBytes().toByteBuffer();
    }

    /**
     * Converts a response body into an array of bytes.
     *
     * {{{
     * byte[] byteArray = response.body(bytes())
     * }}}
     */
    default BodyReadable<byte[]> bytes() {
        return response -> response.getBodyAsBytes().toArray();
    }

    default BodyReadable<InputStream> inputStream() {
        return (response -> new ByteArrayInputStream(response.getBodyAsBytes().toArray()));
    }

    default BodyReadable<Source<ByteString, ?>> source() {
        return StandaloneWSResponse::getBodyAsSource;
    }

}
