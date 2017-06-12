/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * This interface defines a set of reads for converting a response
 * into a readable format.
 */
public interface DefaultBodyReadables {

    /**
     * Converts a response body into an akka.util.ByteString:
     *
     * {{{
     * ByteString byteString = response.body(byteString())
     * }}}
     */
    default BodyReadable<ByteString> byteString() {
        return StandaloneWSResponse::getBodyAsBytes;
    }

    /**
     * Converts a response body into a String:
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
