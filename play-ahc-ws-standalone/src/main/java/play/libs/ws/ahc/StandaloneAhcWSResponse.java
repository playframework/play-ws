/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.util.ByteString;

import play.api.libs.ws.ahc.AhcWSUtils;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;
import play.libs.ws.WSCookieBuilder;

import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.io.netty.handler.codec.http.cookie.Cookie;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static java.util.stream.Collectors.toList;

/**
 * A WS response.
 */
public class StandaloneAhcWSResponse implements StandaloneWSResponse {

    private final play.shaded.ahc.org.asynchttpclient.Response ahcResponse;

    public StandaloneAhcWSResponse(play.shaded.ahc.org.asynchttpclient.Response ahcResponse) {
        this.ahcResponse = ahcResponse;
    }

    @Override
    public Object getUnderlying() {
        return this.ahcResponse;
    }

    /**
     * Get the HTTP status code of the response
     */
    @Override
    public int getStatus() {
        return ahcResponse.getStatusCode();
    }

    /**
     * Get the HTTP status text of the response
     */
    @Override
    public String getStatusText() {
        return ahcResponse.getStatusText();
    }

    /**
     * Get all the HTTP headers of the response as a case-insensitive map
     */
    @Override
    public Map<String, List<String>> getHeaders() {
        final Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final HttpHeaders headers = ahcResponse.getHeaders();
        for (String name : headers.names()) {
            final List<String> values = headers.getAll(name);
            headerMap.put(name, values);
        }
        return headerMap;
    }

    /**
     * Get all the cookies.
     */
    @Override
    public List<WSCookie> getCookies() {
        return ahcResponse.getCookies().stream().map(this::asCookie).collect(toList());
    }

    public WSCookie asCookie(Cookie c) {
       return new WSCookieBuilder()
                .setName(c.name())
                .setValue(c.value())
                .setDomain(c.domain())
                .setPath(c.path())
                .setMaxAge(c.maxAge())
                .setSecure(c.isSecure())
                .setHttpOnly(c.isHttpOnly()).build();
    }

    /**
     * Get only one cookie, using the cookie name.
     */
    @Override
    public Optional<WSCookie> getCookie(String name) {
        for (Cookie ahcCookie : ahcResponse.getCookies()) {
            // safe -- cookie.getName() will never return null
            if (ahcCookie.name().equals(name)) {
                return Optional.of(asCookie(ahcCookie));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getBody() {
        return AhcWSUtils.getResponseBody(ahcResponse);
    }

    @Override
    public ByteString getBodyAsBytes() {
        return ByteString.fromArray(this.ahcResponse.getResponseBodyAsBytes());
    }

    @Override
    public <T> T getBody(BodyReadable<T> readable) {
        return readable.apply(this);
    }

    @Override
    public String getContentType() {
        String contentType = ahcResponse.getContentType();
        if (contentType == null) {
            // As defined by RFC-2616#7.2.1
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    /**
     * Return the request {@link URI}. Note that if the request got redirected, the value of the
     * {@link URI} will be the last valid redirect url.
     *
     * @return the request {@link URI}.
     */
    public URI getUri() {
        try {
            return ahcResponse.getUri().toJavaNetURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
