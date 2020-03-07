/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;


import java.time.Duration;
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
    // Note that you should not use default methods here, because if you call
    // a Java API from specs2 or another scala class, then the linearization
    // will happen differently and cause the existential to be captured rather
    // than the type refined API:
    //
    // val ws: WSClient = ...
    // val res: CompletionStage[WSResponse] = ws.url("/get").stream()
    //
    //[error]  found   : java.util.concurrent.CompletionStage[?0] where type ?0 <: play.libs.ws.StandaloneWSResponse
    //[error]  required: java.util.concurrent.CompletionStage[play.libs.ws.WSResponse]
    //[error]       val futureResponse: CompletionStage[WSResponse] = request.get()
    //
    // This would make an excellent Scala puzzler, because it's utterly unintuitive
    // while still being completely to the scala compiler spec, and will work fine
    // in a Java test.

    //-------------------------------------------------------------------------
    // "GET"
    //-------------------------------------------------------------------------

    /**
     * Perform a GET on the request asynchronously.
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> get();

    //-------------------------------------------------------------------------
    // "PATCH"
    //-------------------------------------------------------------------------

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body the BodyWritable
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> patch(BodyWritable body);

    //-------------------------------------------------------------------------
    // "POST"
    //-------------------------------------------------------------------------

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body the BodyWritable
     * @return a promise to the response
     */
     CompletionStage<? extends StandaloneWSResponse> post(BodyWritable body);

    //-------------------------------------------------------------------------
    // "PUT"
    //-------------------------------------------------------------------------

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body the BodyWritable
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> put(BodyWritable body);

    //-------------------------------------------------------------------------
    // Miscellaneous execution methods
    //-------------------------------------------------------------------------

    /**
     * Perform a DELETE on the request asynchronously.
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> delete();

    /**
     * Perform a HEAD on the request asynchronously.
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> head();

    /**
     * Perform an OPTIONS on the request asynchronously.
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> options();

    /**
     * Executes an arbitrary method on the request asynchronously.
     *
     * @param method The method to execute
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> execute(String method);

    /**
     * Executes an arbitrary method on the request asynchronously.  Should be used with setMethod().
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> execute();

    /**
     * Executes this request and streams the response body.
     *
     * Use {@code response.bodyAsSource()} with this method.
     *
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> stream();

    //-------------------------------------------------------------------------
    // Setters
    //-------------------------------------------------------------------------

    /**
     * Sets the URL of the request.
     *
     * @param url the URL of the request
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setUrl(String url);

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
    StandaloneWSRequest setBody(BodyWritable body);

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
    StandaloneWSRequest addCookies(WSCookie... cookies);

    /**
     * Returns this request with the given cookies, discarding the existing ones. In general, cookies set on a request
     * are used only on that request and then discarded. But when `play.ws.ahc.useCookieStore` is enabled, there will be a
     * global cookie store that keeps cookies between requests.
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
    default StandaloneWSRequest setAuth(String username, String password) {
        return setAuth(new WSAuthInfo(username, password, WSAuthScheme.BASIC));
    }

    /**
     * Sets the authentication header for the current request.
     *
     * @param username the username
     * @param password the password
     * @param scheme   authentication scheme
     * @return the modified WSRequest.
     */
    default StandaloneWSRequest setAuth(String username, String password, WSAuthScheme scheme) {
        return setAuth(new WSAuthInfo(username, password, scheme));
    }

    /**
     * Sets the authentication header for the current request.
     *
     * @param authInfo the authentication information.
     *
     * @return the modified request.
     */
    StandaloneWSRequest setAuth(WSAuthInfo authInfo);

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

    /**
     * Sets the proxy server.
     *
     * @param proxyServer the proxy server
     * @return the modified WSRequest
     */
    StandaloneWSRequest setProxyServer(WSProxyServer proxyServer);

    //-------------------------------------------------------------------------
    // Getters
    //-------------------------------------------------------------------------

    /**
     * @return the URL of the request.  This has not passed through an internal request builder and so will not be signed.
     */
    String getUrl();

    /**
     * @return the HTTP method of the request.
     */
    default String getMethod() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the cookies (a copy to prevent side-effects).
     */
    default List<WSCookie> getCookies() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the body of the request.
     */
    Optional<BodyWritable> getBody();

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
    List<String> getHeaderValues(String name);

    /**
     * Get the value of the header with the specified name. If there are more than one values
     * for this header, the first value is returned. If there are no values, than an empty
     * Optional is returned.
     *
     * @param name the header name
     * @return the header value
     */
    Optional<String> getHeader(String name);

    /**
     * @return the query parameters (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
     */
    Map<String, List<String>> getQueryParameters();

    /**
     * @return the auth username, Optional.empty() if not an authenticated request.
     */
    default Optional<String> getUsername() {
        return getAuth().map(WSAuthInfo::getUsername);
    }

    /**
     * @return the auth password, Optional.empty() if not an authenticated request
     */
    default Optional<String> getPassword() {
        return getAuth().map(WSAuthInfo::getPassword);
    }

    /**
     * @return the auth scheme, Optional.empty() if not an authenticated request.
     */
    default Optional<WSAuthScheme> getScheme() {
        return getAuth().map(WSAuthInfo::getScheme);
    }

    /**
     * @return get auth information if present.
     *
     * @see WSAuthInfo
     */
    Optional<WSAuthInfo> getAuth();

    Optional<WSProxyServer> getProxyServer();

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
