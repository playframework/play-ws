package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

public class SourceBody implements WSBody {
    private final Source<ByteString, ?> source;

    public SourceBody(Source<ByteString, ?> source) {
        this.source = source;
    }

    public Source<ByteString, ?> source() {
        return source;
    }
}
