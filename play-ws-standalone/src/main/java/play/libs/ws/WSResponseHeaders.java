/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WSResponseHeaders {

    /**
     * @return the http response status
     */
    int getStatus();

    /**
     * @return all the headers.
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
     * An alias method for {@link #getSingleHeader(String)}.
     *
     * @param name the header's name
     * @return a single header value from the response.
     */
    default Optional<String> getHeader(String name) {
        return getSingleHeader(name);
    }

}
