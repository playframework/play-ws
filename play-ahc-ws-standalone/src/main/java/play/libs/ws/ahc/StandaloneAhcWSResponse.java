/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.reactivestreams.Publisher;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
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
        return ahcResponse.getCookies().stream().map(AhcWSCookie::new).collect(toList());
    }

    /**
     * Get only one cookie, using the cookie name.
     */
    @Override
    public WSCookie getCookie(String name) {
        for (play.shaded.ahc.org.asynchttpclient.cookie.Cookie ahcCookie : ahcResponse.getCookies()) {
            // safe -- cookie.getName() will never return null
            if (ahcCookie.getName().equals(name)) {
                return (new AhcWSCookie(ahcCookie));
            }
        }
        return null;
    }

    @Override
    public String getBody() {
        // https://tools.ietf.org/html/rfc7231#section-3.1.1.3
        // https://tools.ietf.org/html/rfc7231#appendix-B
        // The default charset of ISO-8859-1 for text media types has been
        // removed; the default is now whatever the media type definition says.
        Response underlying = (Response) getUnderlying();
        return underlying.getResponseBody();
    }

    @Override
    public ByteString getBodyAsBytes() {
        Response underlying = (Response) getUnderlying();
        return ByteString.fromArray(underlying.getResponseBodyAsBytes());
    }

    @Override
    public Source<ByteString, ?> getBodyAsSource() {
        @SuppressWarnings("unchecked") Publisher<HttpResponseBodyPart> publisher = (Publisher<HttpResponseBodyPart>) getUnderlying();
        return Source.fromPublisher(publisher).map(bodyPart -> ByteString.fromArray(bodyPart.getBodyPartBytes()));
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
