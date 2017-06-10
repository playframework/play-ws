/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * This interface contains useful {@link BodyWritable} subclasses and default methods.
 *
 * Most of the time, you'll want to implement this interface and then use the static factory methods:
 *
 * <pre>
 * {@code
 * public class MyClient implements DefaultBodyWritables {
 *     public void doStuff() {
 *         wsClient.url("http://example.com").post(body("hello world")).thenApply(response ->
 *             ...
 *         );
 *     }
 * }
 * }
 * </pre>
 *
 * You can always instantiate the writables directly using other methods as necessary:
 *
 * <pre>
 * {@code
 * public class MyClient {
 *     private BodyWritable<String> someOtherMethod(String string) {
 *       return new InMemoryBodyWritable(ByteString.fromString(string), "text/plain");
 *     }
 *
 *     public void doStuff() {
 *         wsClient.url("http://example.com").post(someOtherMethod("hello world")).thenApply(response ->
 *             ...
 *         );
 *     }
 * }
 * }
 * </pre>
 */
public interface DefaultBodyWritables {

    /**
     * Creates a {@link InMemoryBodyWritable} from a String, setting the content-type to "text/plain".
     *
     * @param s the string to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(String s) {
        return body(s,"text/plain");
    }

    /**
     * Creates a {@link InMemoryBodyWritable} from a String.
     *
     * @param s the string to pass in.
     * @param contentType to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(String s, String contentType) {
        return new InMemoryBodyWritable(ByteString.fromString(s), contentType);
    }

    /**
     * Creates a {@link InMemoryBodyWritable} from a byte array.
     *
     * @param array the byte array to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(byte[] array) {
        return body(array, "application/octet-stream");
    }

    /**
     * Creates a {@link InMemoryBodyWritable} from a byte array.
     *
     * @param array the byte array to pass in.
     * @param contentType to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(byte[] array, String contentType) {
        return new InMemoryBodyWritable(ByteString.fromArray(array), contentType);
    }

    /**
     * Creates a {@link InMemoryBodyWritable} from a byte array using "application/octet-stream"
     * as the content type.
     *
     * @param buffer the byte array to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(ByteBuffer buffer) {
        return body(buffer, "application/octet-stream");
    }

    /**
     * Creates a {@link InMemoryBodyWritable} from a byte array.
     *
     * @param buffer the byte array to pass in.
     * @param contentType to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(ByteBuffer buffer, String contentType) {
        return new InMemoryBodyWritable(ByteString.fromByteBuffer(buffer), contentType);
    }

    /**
     * Creates a {@link SourceBodyWritable}, streaming the body incrementally, setting the content-type to "application/octet-stream".
     *
     * @param source the source of byte string.
     * @return a {@link SourceBodyWritable} instance.
     */
    default BodyWritable<Source<ByteString, ?>> body(Source<ByteString, ?> source) {
        return new SourceBodyWritable(source);
    }

    /**
     * Creates a {@link SourceBodyWritable}, setting the content-type to "application/octet-stream".
     *
     * @param file the file
     * @return a {@link SourceBodyWritable} instance.
     */
    default BodyWritable<Source<ByteString, ?>> body(File file) {
        return new SourceBodyWritable(FileIO.fromFile(file));
    }

    /**
     * Creates a {@link SourceBodyWritable}, setting the content-type to "application/octet-stream".
     *
     * @param is the inputstream
     * @return a {@link SourceBodyWritable} instance.
     */
    default BodyWritable<Source<ByteString, ?>> body(Supplier<InputStream> is) {
        return new SourceBodyWritable(StreamConverters.fromInputStream(is::get));
    }

}

