/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;


import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * This is the main interface to building a WS request in Java.
 * <p>
 * Note that this interface does not expose properties that are only exposed
 * after building the request: notably, the URL, headers and query parameters
 * are shown before an OAuth signature is calculated.
 */
public interface StandaloneWSRequest {

    //-------------------------------------------------------------------------
    // "GET"
    //-------------------------------------------------------------------------

    /**
     * Perform a GET on the request asynchronously.
     *
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> get() {
        return execute("GET");
    }

    //-------------------------------------------------------------------------
    // "PATCH"
    //-------------------------------------------------------------------------

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body represented as body
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> patch(WSBody body) {
        return setMethod("PATCH").setBody(body).execute();
    }

    //-------------------------------------------------------------------------
    // "POST"
    //-------------------------------------------------------------------------

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body represented as body
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> post(WSBody body) {
        return setMethod("POST").setBody(body).execute();
    }

    //-------------------------------------------------------------------------
    // "PUT"
    //-------------------------------------------------------------------------

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body represented as body
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> put(WSBody body) {
        return setMethod("PUT").setBody(body).execute();
    }

    //-------------------------------------------------------------------------
    // Miscellaneous execution methods
    //-------------------------------------------------------------------------

    /**
     * Perform a DELETE on the request asynchronously.
     *
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> delete() {
        return execute("DELETE");
    }

    /**
     * Perform a HEAD on the request asynchronously.
     *
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> head() {
        return execute("HEAD");
    }

    /**
     * Perform an OPTIONS on the request asynchronously.
     *
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> options() {
        return execute("OPTIONS");
    }

    /**
     * Execute an arbitrary method on the request asynchronously.
     *
     * @param method The method to execute
     * @return a promise to the response
     */
    default CompletionStage<? extends StandaloneWSResponse> execute(String method) {
        return setMethod(method).execute();
    }

    /**
     * Execute an arbitrary method on the request asynchronously.  Should be used with setMethod().
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> execute();

    /**
     * Execute this request and stream the response body.
     *
     * @return a promise to the streaming response
     */
    CompletionStage<? extends StreamedResponse> stream();

    //-------------------------------------------------------------------------
    // Setters
    //-------------------------------------------------------------------------

    /**
     * Sets the HTTP method this request should use, where the no args execute() method is invoked.
     *
     * @param method the HTTP method.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setMethod(String method);

    /**
     * Set the body this request should use.
     *
     * @param body the body of the request.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setBody(WSBody body);

    /**
     * Set headers to the request.  Note that duplicate headers are allowed
     * by the HTTP specification, and removing a header is not available
     * through this API. Any existing header will be discarded here.
     *
     * @param headers the headers
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setHeaders(Map<String, List<String>> headers);

    /**
     * Adds a header to the request.  Note that duplicate headers are allowed
     * by the HTTP specification, and removing a header is not available
     * through this API. Existent headers will be preserved.
     *
     * @param name  the header name
     * @param value the header value
     * @return the modified WSRequest.
     */
    StandaloneWSRequest addHeader(String name, String value);

    /**
     * Sets the query string to query.
     *
     * @param query the fully formed query string
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setQueryString(String query);

    /**
     * Adds a query parameter with the given name, this can be called repeatedly and will preserve existing values.
     * Duplicate query parameters are allowed.
     *
     * @param name  the query parameter name
     * @param value the query parameter value
     * @return the modified WSRequest.
     */
    StandaloneWSRequest addQueryParameter(String name, String value);

    /**
     * Sets the query string parameters. This will discard existing values.
     *
     * @param params the query string parameters
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setQueryString(Map<String, List<String>> params);

    /**
     * Add a new cookie. This can be called repeatedly and will preserve existing cookies.
     *
     * @param cookie the cookie to be added
     * @return the modified WSRequest.
     *
     * @see #addCookies(WSCookie...)
     * @see #setCookies(List)
     */
    StandaloneWSRequest addCookie(WSCookie cookie);

    /**
     * Add new cookies. This can be called repeatedly and will preserve existing cookies.
     *
     * @param cookies the list of cookies to be added
     * @return the modified WSRequest.
     *
     * @see #addCookie(WSCookie)
     * @see #setCookies(List)
     */
    StandaloneWSRequest addCookies(WSCookie ... cookies);

    /**
     * Set the request cookies. This discard the existing cookies.
     *
     * @param cookies the cookies to be used.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setCookies(List<WSCookie> cookies);

    /**
     * Sets the authentication header for the current request using BASIC authentication.
     *
     * @param userInfo a string formed as "username:password".
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setAuth(String userInfo);

    /**
     * Sets the authentication header for the current request using BASIC authentication.
     *
     * @param username the basic auth username
     * @param password the basic auth password
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setAuth(String username, String password);

    /**
     * Sets the authentication header for the current request.
     *
     * @param username the username
     * @param password the password
     * @param scheme   authentication scheme
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setAuth(String username, String password, WSAuthScheme scheme);

    /**
     * Sets an (OAuth) signature calculator.
     *
     * @param calculator the signature calculator
     * @return the modified WSRequest
     */
    StandaloneWSRequest sign(WSSignatureCalculator calculator);

    /**
     * Sets whether redirects (301, 302) should be followed automatically.
     *
     * @param followRedirects true if the request should follow redirects
     * @return the modified WSRequest
     */
    StandaloneWSRequest setFollowRedirects(boolean followRedirects);

    /**
     * Sets the virtual host as a "hostname:port" string.
     *
     * @param virtualHost the virtual host
     * @return the modified WSRequest
     */
    StandaloneWSRequest setVirtualHost(String virtualHost);

    /**
     * Sets the request timeout duration. Java {@link Duration} class does not have a specific instance
     * to represent an infinite timeout, but according to the docs, in practice, you can somehow emulate
     * it:
     *
     * <blockquote>
     *     A physical duration could be of infinite length. For practicality, the duration is stored
     *     with constraints similar to Instant. The duration uses nanosecond resolution with a maximum
     *     value of the seconds that can be held in a long. This is greater than the current estimated
     *     age of the universe.
     * </blockquote>
     *
     * Play WS uses the convention of setting a duration with negative value to have an infinite timeout.
     * So you will have:
     *
     * <pre>java.time.Duration timeout = Duration.ofSeconds(-1);</pre>.
     *
     * In practice, you can also have an extreme long duration, like:
     *
     * <pre>java.time.Duration timeout = Duration.ofMillis(Long.MAX_VALUE);</pre>
     *
     * And, as the {@link Duration} docs states, this will be good enough since this duration is greater than
     * the current estimate age of the universe.
     *
     * @param timeout the request timeout in milliseconds. A duration of -1 indicates an infinite request timeout.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setRequestTimeout(Duration timeout);

    /**
     * Adds a request filter.
     *
     * @param filter a transforming filter.
     * @return the modified request.
     */
    StandaloneWSRequest setRequestFilter(WSRequestFilter filter);

    /**
     * Set the content type.  If the request body is a String, and no charset parameter is included, then it will
     * default to UTF-8.
     *
     * @param contentType The content type
     * @return the modified WSRequest
     */
    StandaloneWSRequest setContentType(String contentType);

    //-------------------------------------------------------------------------
    // Getters
    //-------------------------------------------------------------------------

    /**
     * @return the URL of the request.  This has not passed through an internal request builder and so will not be signed.
     */
    String getUrl();

    /**
     * @return the headers (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
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
    default Optional<String> getHeader(String name) {
        return getHeaderValues(name).stream().findFirst();
    }

    /**
     * @return the query parameters (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
     */
    Map<String, List<String>> getQueryParameters();

    /**
     * @return the auth username, Optional.empty() if not an authenticated request.
     */
    Optional<String> getUsername();

    /**
     * @return the auth password, Optional.empty() if not an authenticated request
     */
    Optional<String> getPassword();

    /**
     * @return the auth scheme, Optional.empty() if not an authenticated request.
     */
    Optional<WSAuthScheme> getScheme();

    /**
     * @return the signature calculator (example: OAuth), or Optional.empty() if none is set.
     */
    Optional<WSSignatureCalculator> getCalculator();

    /**
     * Gets the original request timeout duration, passed into the request as input.
     *
     * @return the timeout duration.
     */
    Optional<Duration> getRequestTimeout();

    /**
     * @return true if the request is configure to follow redirect, false if it is configure not to, Optional.empty() if nothing is configured and the global client preference should be used instead.
     */
    Optional<Boolean> getFollowRedirects();

    /**
     * @return the content type, if any, or Optional.empty() if no content type is found.
     */
    Optional<String> getContentType();
}
