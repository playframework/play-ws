/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;


import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
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
    CompletionStage<? extends StandaloneWSResponse> get();

    //-------------------------------------------------------------------------
    // "PATCH"
    //-------------------------------------------------------------------------

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body represented as String
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> patch(String body);

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body represented as JSON
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> patch(JsonNode body);

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body represented as an InputStream
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> patch(InputStream body);

    /**
     * Perform a PATCH on the request asynchronously.
     *
     * @param body represented as a File
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> patch(File body);

    //-------------------------------------------------------------------------
    // "POST"
    //-------------------------------------------------------------------------

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body represented as String
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> post(String body);

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body represented as JSON
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> post(JsonNode body);

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body represented as an InputStream
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> post(InputStream body);

    /**
     * Perform a POST on the request asynchronously.
     *
     * @param body represented as a File
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> post(File body);

    //-------------------------------------------------------------------------
    // "PUT"
    //-------------------------------------------------------------------------

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body represented as String
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> put(String body);

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body represented as JSON
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> put(JsonNode body);

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body represented as an InputStream
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> put(InputStream body);

    /**
     * Perform a PUT on the request asynchronously.
     *
     * @param body represented as a File
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> put(File body);

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
     * Execute an arbitrary method on the request asynchronously.
     *
     * @param method The method to execute
     * @return a promise to the response
     */
    CompletionStage<? extends StandaloneWSResponse> execute(String method);

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
    StandaloneWSRequest setBody(String body);

    /**
     * Set the body this request should use.
     *
     * @param body the body of the request.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setBody(JsonNode body);

    /**
     * Set the body this request should use.
     *
     * @param body Deprecated
     * @return Deprecated
     * @deprecated use {@link #setBody(Source)} instead.
     */
    @Deprecated
    StandaloneWSRequest setBody(InputStream body);

    /**
     * Set the body this request should use.
     *
     * @param body the body of the request.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setBody(File body);

    /**
     * Set the body this request should use.
     *
     * @param body the body of the request.
     * @return the modified WSRequest.
     */
    <U> StandaloneWSRequest setBody(Source<ByteString, U> body);

    /**
     * Adds a header to the request.  Note that duplicate headers are allowed
     * by the HTTP specification, and removing a header is not available
     * through this API.
     *
     * @param name  the header name
     * @param value the header value
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setHeader(String name, String value);

    /**
     * Sets the query string to query.
     *
     * @param query the fully formed query string
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setQueryString(String query);

    /**
     * Sets a query parameter with the given name, this can be called repeatedly.  Duplicate query parameters are allowed.
     *
     * @param name  the query parameter name
     * @param value the query parameter value
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setQueryParameter(String name, String value);

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
     * Sets the request timeout in milliseconds.
     *
     * @param timeout the request timeout in milliseconds. A value of -1 indicates an infinite request timeout.
     * @return the modified WSRequest.
     */
    StandaloneWSRequest setRequestTimeout(long timeout);

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
    Map<String, Collection<String>> getHeaders();

    /**
     * @return the query parameters (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
     */
    Map<String, Collection<String>> getQueryParameters();

    /**
     * @return the auth username, null if not an authenticated request.
     */
    String getUsername();

    /**
     * @return the auth password, null if not an authenticated request
     */
    String getPassword();

    /**
     * @return the auth scheme, null if not an authenticated request.
     */
    WSAuthScheme getScheme();

    /**
     * @return the signature calculator (example: OAuth), null if none is set.
     */
    WSSignatureCalculator getCalculator();

    /**
     * Gets the original request timeout in milliseconds, passed into the
     * request as input.
     *
     * @return the timeout
     */
    long getRequestTimeout();

    /**
     * @return true if the request is configure to follow redirect, false if it is configure not to, null if nothing is configured and the global client preference should be used instead.
     */
    boolean getFollowRedirects();

}
