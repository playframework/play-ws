/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils;
import org.w3c.dom.Document;

import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.toList;

/**
 * A WS response.
 */
public class StandaloneAhcWSResponse implements StandaloneWSResponse {

    private final play.shaded.ahc.org.asynchttpclient.Response ahcResponse;
    private final ObjectMapper objectMapper;

    public StandaloneAhcWSResponse(play.shaded.ahc.org.asynchttpclient.Response ahcResponse, ObjectMapper mapper) {
        this.ahcResponse = ahcResponse;
        this.objectMapper = mapper;
    }

    public StandaloneAhcWSResponse(play.shaded.ahc.org.asynchttpclient.Response ahcResponse) {
        this(ahcResponse, StandaloneAhcWSClient.DEFAULT_OBJECT_MAPPER);
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
    public Map<String, List<String>> getAllHeaders() {
        final Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final HttpHeaders headers = ahcResponse.getHeaders();
        for (String name : headers.names()) {
            final List<String> values = headers.getAll(name);
            headerMap.put(name, values);
        }
        return headerMap;
    }

    /**
     * Get the given HTTP header of the response
     */
    @Override
    public String getHeader(String key) {
        return ahcResponse.getHeader(key);
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
                return new AhcWSCookie(ahcCookie);
            }
        }
        return null;
    }

    private String contentType() {
        String contentType = ahcResponse.getContentType();
        if (contentType == null) {
            // As defined by RFC-2616#7.2.1
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    public String getBody() {
        // RFC-2616#3.7.1 states that any text/* mime type should default to ISO-8859-1 charset if not
        // explicitly set, while Plays default encoding is UTF-8.  So, use UTF-8 if charset is not explicitly
        // set and content type is not text/*, otherwise default to ISO-8859-1
        String contentType = contentType();
        Charset charset = HttpUtils.parseCharset(contentType);

        if (charset != null) {
            return ahcResponse.getResponseBody(charset);
        } else if (contentType.startsWith("text/")) {
            return ahcResponse.getResponseBody(HttpUtils.DEFAULT_CHARSET);
        } else {
            return ahcResponse.getResponseBody(StandardCharsets.UTF_8);
        }
    }

    /**
     * Get the response body as a {@link Document DOM document}
     *
     * @return a DOM document
     */
    @Override
    public Document asXml() {
        String contentType = contentType();
        Charset charset = HttpUtils.parseCharset(contentType);
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        return XML.fromInputStream(ahcResponse.getResponseBodyAsStream(), charset.name());
    }

    /**
     * Get the response body as a {@link JsonNode}
     *
     * @return the json response
     */
    @Override
    public JsonNode asJson() {
        // Jackson will automatically detect the correct encoding according to the rules in RFC-4627
        try {
            return objectMapper.readValue(ahcResponse.getResponseBodyAsStream(), JsonNode.class);
        } catch(IOException e) {
            throw new RuntimeException("Error parsing JSON from WS response body", e);
        }
    }

    /**
     * Get the response body as a stream
     *
     * @return The stream to read the response body from
     */
    @Override
    public InputStream getBodyAsStream() {
        return ahcResponse.getResponseBodyAsStream();
    }

    /**
     * Get the response body as a byte array
     *
     * @return The byte array
     */
    @Override
    public byte[] asByteArray() {
        return ahcResponse.getResponseBodyAsBytes();
    }

    /**
     * Return the request {@link URI}. Note that if the request got redirected, the value of the
     * {@link URI} will be the last valid redirect url.
     *
     * @return the request {@link URI}.
     */
    @Override
    public URI getUri() {
        try {
            return ahcResponse.getUri().toJavaNetURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
