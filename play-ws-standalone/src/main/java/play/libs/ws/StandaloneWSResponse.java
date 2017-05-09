/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import play.libs.ws.WSCookie;

import java.io.InputStream;
import java.net.URI;
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
     *
     * @deprecated Since 1.0.0. Use {@link #getHeaders()} instead.
     */
    @Deprecated
    default Map<String, List<String>> getAllHeaders() {
        return getHeaders();
    }

    /**
     * @param key the header's name
     * @return a single header value from the response.
     *
     * @deprecated Since 1.0.0. Use {@link #getSingleHeader(String)} instead.
     */
    @Deprecated
    default String getHeader(String key) {
        return getSingleHeader(key).orElse(null);
    }

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
    WSCookie getCookie(String name);

    /**
     * @return the body as a string.
     */
    String getBody();

    /**
     * @return the body as XML.
     */
    Document asXml();

    /**
     * @return the body as JSON node.
     */
    JsonNode asJson();

    /**
     * @return the body as a stream.
     */
    InputStream getBodyAsStream();

    /**
     * @return the body as an array of bytes.
     */
    byte[] asByteArray();

    /**
     * @return the URI of the response.
     */
    URI getUri();
}
