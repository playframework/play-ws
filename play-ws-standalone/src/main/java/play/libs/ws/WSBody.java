package play.libs.ws;

/**
 * A WS body marker interface.  The client is responsible for creating these instances:
 * <p>
 * <pre>
 * @{code
 *   StandaloneAhcWSClient client = ...
 *   WSBody stringBody = client.body("Hello world!");
 *   request.setBody(stringBody);
 * }
 * </pre>
 */
public interface WSBody<T> {

    T body();
}


