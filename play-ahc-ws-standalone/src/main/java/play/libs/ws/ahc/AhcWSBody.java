package play.libs.ws.ahc;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.ws.WSBody;

import java.io.File;
import java.io.InputStream;

/**
 * This class creates WSBody instances from String, json, file, or inputstream.
 * <p>
 * It is accessed from the StandaloneAhcWSClient.
 */
class AhcWSBody<T> implements WSBody<T> {

    private T instance;

    AhcWSBody(T instance) {
        this.instance = instance;
    }

    public T body() {
        return this.instance;
    }

    public static WSBody<String> string(String body) {
        return new AhcWSBody<>(body);
    }

    public static WSBody<JsonNode> json(JsonNode body) {
        return new AhcWSBody<>(body);
    }

    public static WSBody<Source<ByteString, ?>> source(Source<ByteString, ?> body) {
        return new AhcWSBody<>(body);
    }

    public static WSBody<File> file(File body) {
        return new AhcWSBody<>(body);
    }

    public static WSBody<InputStream> inputStream(InputStream body) {
        return new AhcWSBody<>(body);
    }

    public static WSBody<Object> empty() {
        return () -> null;
    }
}