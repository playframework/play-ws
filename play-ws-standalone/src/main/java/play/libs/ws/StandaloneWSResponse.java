/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This is the WS response from the server.
 */
public interface StandaloneWSResponse {

    /**
     * @return all the headers from the response.
     */
    Map<String, List<String>> getHeaders();

    /**
     * Get all the values of header with the specified name. If there are no values for
     * the header with the specified name, than an empty List is returned.
     *
     * @param name the header name.
     * @return all the values for this header name.
     */
    default List<String> getHeaderValues(String name) {
        return getHeaders().getOrDefault(name, Collections.emptyList());
    }

    /**
     * Get the value of the header with the specified name. If there are more than one values
     * for this header, the first value is returned. If there are no values, than an empty
     * Optional is returned.
     *
     * @param name the header name
     * @return the header value
     */
    default Optional<String> getSingleHeader(String name) {
        return getHeaderValues(name).stream().findFirst();
    }

    /**
     * @return the underlying implementation response object, if any.
     */
    Object getUnderlying();

    /**
     * @return the HTTP status code from the response.
     */
    int getStatus();

    /**
     * @return the text associated with the status code.
     */
    String getStatusText();

    /**
     * @return all the cookies from the response.
     */
    List<WSCookie> getCookies();

    /**
     * @param name the cookie name
     * @return a single cookie from the response, if any.
     */
    Optional<WSCookie> getCookie(String name);

    /**
     * @return the content type.
     */
    String getContentType();

    /**
     * Returns the response getBody as a particular type, through a
     * {@link BodyReadable} transformation.  You can define your
     * own {@link BodyReadable} types:
     *
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
     *
     * or use {@code play.libs.ws.ahc.DefaultResponseReadables}
     * for the built-ins:
     *
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
     * @param <T> the end type to return
     * @return the response getBody transformed into an instance of T
     */
    <T> T getBody(BodyReadable<T> readable);

    String getBody();

    ByteString getBodyAsBytes();

    /**
     * Converts a response body into Source[ByteString, NotUsed].
     *
     * Note that this is only usable with a streaming request:
     *
     */
    Source<ByteString, ?> getBodyAsSource();
}
