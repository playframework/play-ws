/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.net.URI;

/**
 * This is the WS response from the server.
 */
public interface StandaloneWSResponse {
    /**
     * Gets the response URI.
     *
     * @return the response URI
     */
    URI getUri();

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

    /**
     * The response body decoded as String, using a simple algorithm to guess the encoding.
     *
     * This decodes the body to a string representation based on the following algorithm:
     *
     *  1. Look for a "charset" parameter on the Content-Type. If it exists, set `charset` to its value and goto step 3.
     *  2. If the Content-Type is of type "text", set $charset to "ISO-8859-1"; else set `charset` to "UTF-8".
     *  3. Decode the raw bytes of the body using `charset`.
     *
     * Note that this does not take into account any special cases for specific content types. For example, for
     * application/json, we do not support encoding autodetection and will trust the charset parameter if provided.
     *
     * @return the response body parsed as a String using the above algorithm.
     */
    String getBody();

    ByteString getBodyAsBytes();

    /**
     * Converts a response body into Source[ByteString, NotUsed].
     *
     * Note that this is only usable with a streaming request:
     *
     * <pre>
     * {@code
     * wsClient.url("https://playframework.com")
     *         .stream() // this returns a CompletionStage<StandaloneWSResponse>
     *         .thenApply(StandaloneWSResponse::getBodyAsSource);
     * }
     * </pre>
     */
    default Source<ByteString, ?> getBodyAsSource() {
        return Source.single(getBodyAsBytes());
    }
}
