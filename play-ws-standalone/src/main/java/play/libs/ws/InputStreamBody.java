package play.libs.ws;

import java.io.InputStream;

public class InputStreamBody implements WSBody {
    private final InputStream inputStream;

    public InputStreamBody(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public InputStream inputStream() {
        return inputStream;
    }
}
