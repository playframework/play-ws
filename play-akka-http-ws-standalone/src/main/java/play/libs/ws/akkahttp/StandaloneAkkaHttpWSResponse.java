package play.libs.ws.akkahttp;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StandaloneAkkaHttpWSResponse implements StandaloneWSResponse {

  private final play.api.libs.ws.StandaloneWSResponse response;

  StandaloneAkkaHttpWSResponse(play.api.libs.ws.StandaloneWSResponse response) {
    this.response = response;
  }

  /**
   * @return all the headers from the response.
   */
  @Override
  public Map<String, List<String>> getHeaders() {
    return null;
  }

  /**
   * @return the underlying implementation response object, if any.
   */
  @Override
  public Object getUnderlying() {
    return null;
  }

  /**
   * @return the HTTP status code from the response.
   */
  @Override
  public int getStatus() {
    return 0;
  }

  /**
   * @return the text associated with the status code.
   */
  @Override
  public String getStatusText() {
    return null;
  }

  /**
   * @return all the cookies from the response.
   */
  @Override
  public List<WSCookie> getCookies() {
    return null;
  }

  /**
   * @param name the cookie name
   * @return a single cookie from the response, if any.
   */
  @Override
  public Optional<WSCookie> getCookie(String name) {
    return Optional.empty();
  }

  /**
   * @return the content type.
   */
  @Override
  public String getContentType() {
    return null;
  }

  /**
   * Returns the response getBody as a particular type, through a
   * {@link BodyReadable} transformation.  You can define your
   * own {@link BodyReadable} types:
   * <p>
   * <pre>
   * {@code public class MyClass {
   *   private BodyReadable<Foo, StandaloneWSResponse> fooReadable = (response) -> new Foo();
   *
   *   public void readAsFoo(StandaloneWSResponse response) {
   *       Foo foo = response.getBody(fooReadable);
   *   }
   * }
   * }
   * </pre>
   * <p>
   * or use {@code play.libs.ws.ahc.DefaultResponseReadables}
   * for the built-ins:
   * <p>
   * <pre>
   * {@code public class MyClass implements DefaultResponseReadables {
   *     public void readAsString(StandaloneWSResponse response) {
   *         String getBody = response.getBody(string());
   *     }
   *
   *     public void readAsJson(StandaloneWSResponse response) {
   *         JsonNode json = response.getBody(json());
   *     }
   * }
   * }
   * </pre>
   *
   * @param readable the readable to convert the response to a T
   * @return the response getBody transformed into an instance of T
   */
  @Override
  public <T> T getBody(BodyReadable<T> readable) {
    return response.body(new play.api.libs.ws.BodyReadable<T>(r -> readable.apply(new StandaloneAkkaHttpWSResponse(r))));
  }

  /**
   * The response body decoded as String, using a simple algorithm to guess the encoding.
   * <p>
   * This decodes the body to a string representation based on the following algorithm:
   * <p>
   * 1. Look for a "charset" parameter on the Content-Type. If it exists, set `charset` to its value and goto step 3.
   * 2. If the Content-Type is of type "text", set $charset to "ISO-8859-1"; else set `charset` to "UTF-8".
   * 3. Decode the raw bytes of the body using `charset`.
   * <p>
   * Note that this does not take into account any special cases for specific content types. For example, for
   * application/json, we do not support encoding autodetection and will trust the charset parameter if provided.
   *
   * @return the response body parsed as a String using the above algorithm.
   */
  @Override
  public String getBody() {
    return response.body();
  }

  @Override
  public ByteString getBodyAsBytes() {
    return null;
  }

  @Override
  public Source<ByteString, ?> getBodyAsSource() {
    return response.bodyAsSource().asJava();
  }
}
