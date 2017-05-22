package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.InputStream;

public class WSBodyFactory {
    private WSBodyFactory() {

    }

    public static WSBody string(String body) {
        return new StringBody(body);
    }

    public static WSBody json(JsonNode body) {
        return new JsonBody(body);
    }

    public static WSBody source(Source<ByteString, ?> body) {
        return new SourceBody(body);
    }

    public static WSBody file(File body) {
        return new FileBody(body);
    }

    public static WSBody inputStream(InputStream inputStream) {
        return new InputStreamBody(inputStream);
    }
}
