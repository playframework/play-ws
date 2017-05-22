package play.libs.ws;

public class StringBody implements WSBody {
    private final String string;

    public StringBody(String string) {
        this.string = string;
    }

    public String string() {
        return string;
    }
}
