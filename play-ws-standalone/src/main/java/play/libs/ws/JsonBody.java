package play.libs.ws;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonBody implements WSBody {
    private final JsonNode json;

    public JsonBody(JsonNode json) {
        this.json = json;
    }

    public JsonNode json() {
        return json;
    }
}
