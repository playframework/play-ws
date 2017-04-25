/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.*;
import play.shaded.ahc.org.asynchttpclient.request.body.generator.ByteArrayBodyGenerator;
import play.shaded.ahc.org.asynchttpclient.request.body.generator.FileBodyGenerator;
import play.shaded.ahc.org.asynchttpclient.request.body.generator.InputStreamBodyGenerator;
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils;
import org.reactivestreams.Publisher;
import play.api.libs.ws.ahc.FormUrlEncodedParser;

import play.libs.oauth.OAuth;
import play.libs.ws.*;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Provides the User facing API for building a WS request.
 */
public class StandaloneAhcWSRequest implements StandaloneWSRequest {

    private Object body = null;

    private final String url;
    private String method = "GET";
    private final Map<String, List<String>> headers = new HashMap<>();
    private final Map<String, List<String>> queryParameters = new HashMap<>();

    private String username;
    private String password;
    private WSAuthScheme scheme;
    private WSSignatureCalculator calculator;
    private final StandaloneAhcWSClient client;

    private final Materializer materializer;
    private final ObjectMapper objectMapper;

    private int timeout = 0;
    private boolean followRedirects;
    private String virtualHost = null;

    private final List<WSRequestFilter> filters = new ArrayList<>();

    public StandaloneAhcWSRequest(StandaloneAhcWSClient client, String url, Materializer materializer, ObjectMapper mapper) {
        this.client = client;
        URI reference = URI.create(url);

        this.url = url;
        this.materializer = materializer;
        this.objectMapper = mapper;
        String userInfo = reference.getUserInfo();
        if (userInfo != null) {
            this.setAuth(userInfo);
        }
        if (reference.getQuery() != null) {
            this.setQueryString(reference.getQuery());
        }
    }

    public StandaloneAhcWSRequest(StandaloneAhcWSClient client, String url, Materializer materializer) {
        this(client, url, materializer, StandaloneAhcWSClient.DEFAULT_OBJECT_MAPPER);
    }


    @Override
    public StandaloneAhcWSRequest setRequestFilter(WSRequestFilter filter) {
        filters.add(filter);
        return this;
    }

    /**
     * Sets a header with the given name, this can be called repeatedly.
     *
     * @param name  the header name
     * @param value the header value
     * @return the receiving WSRequest, with the new header set.
     */
    @Override
    public StandaloneAhcWSRequest setHeader(String name, String value) {
        addValueTo(headers, name, value);
        return this;
    }

    /**
     * Sets a query string
     *
     * @param query the query string
     */
    @Override
    public StandaloneAhcWSRequest setQueryString(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length > 2) {
                throw new RuntimeException(new MalformedURLException("QueryString parameter should not have more than 2 = per part"));
            } else if (keyValue.length == 2) {
                this.setQueryParameter(keyValue[0], keyValue[1]);
            } else if (keyValue.length == 1 && param.charAt(0) != '=') {
                this.setQueryParameter(keyValue[0], null);
            } else {
                throw new RuntimeException(new MalformedURLException("QueryString part should not start with an = and not be empty"));
            }
        }
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setQueryParameter(String name, String value) {
        addValueTo(queryParameters, name, value);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setAuth(String userInfo) {
        this.scheme = WSAuthScheme.BASIC;

        if (userInfo.equals("")) {
            throw new RuntimeException(new MalformedURLException("userInfo should not be empty"));
        }

        int split = userInfo.indexOf(":");

        if (split == 0) { // We only have a password without user
            this.username = "";
            this.password = userInfo.substring(1);
        } else if (split == -1) { // We only have a username without password
            this.username = userInfo;
            this.password = "";
        } else {
            this.username = userInfo.substring(0, split);
            this.password = userInfo.substring(split + 1);
        }

        return this;
    }

    @Override
    public StandaloneAhcWSRequest setAuth(String username, String password) {
        this.username = username;
        this.password = password;
        this.scheme = WSAuthScheme.BASIC;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setAuth(String username, String password, WSAuthScheme scheme) {
        this.username = username;
        this.password = password;
        this.scheme = scheme;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest sign(WSSignatureCalculator calculator) {
        this.calculator = calculator;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setRequestTimeout(long timeout) {
        if (timeout < -1 || timeout > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Timeout must be between -1 and " + Integer.MAX_VALUE + " inclusive");
        }
        this.timeout = (int) timeout;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setContentType(String contentType) {
        return setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
    }

    @Override
    public String getContentType() {
        final List<String> values = headers.get(HttpHeaders.Names.CONTENT_BASE);
        if (values == null) {
            return null;
        } else {
            return values.get(0);
        }
    }

    @Override
    public StandaloneAhcWSRequest setMethod(String method) {
        this.method = method;
        return this;
    }

    /**
     * Sets a body directly.
     *
     * @param body the body as an unbound object.
     * @return the body directly
     */
    public StandaloneAhcWSRequest setBody(Object body) {
        this.body = body;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setBody(String body) {
        this.body = body;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setBody(JsonNode body) {
        this.body = body;
        return this;
    }

    /**
     * Set the body this request should use.
     *
     * @param body Deprecated
     * @return Deprecated
     * @deprecated use {@link #setBody(Source)} instead.
     */
    @Deprecated
    @Override
    public StandaloneAhcWSRequest setBody(InputStream body) {
        this.body = body;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setBody(File body) {
        this.body = body;
        return this;
    }

    @Override
    public <U> StandaloneAhcWSRequest setBody(Source<ByteString, U> body) {
        this.body = body;
        return this;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        return new HashMap<>(this.headers);
    }

    @Override
    public Map<String, Collection<String>> getQueryParameters() {
        return new HashMap<>(this.queryParameters);
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public WSAuthScheme getScheme() {
        return this.scheme;
    }

    @Override
    public WSSignatureCalculator getCalculator() {
        return this.calculator;
    }

    @Override
    public long getRequestTimeout() {
        return this.timeout;
    }

    @Override
    public boolean getFollowRedirects() {
        return this.followRedirects;
    }

    // Intentionally package public.
    String getVirtualHost() {
        return this.virtualHost;
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> get() {
        return execute("GET");
    }

    //-------------------------------------------------------------------------
    // PATCH
    //-------------------------------------------------------------------------

    @Override
    public CompletionStage<? extends StandaloneWSResponse> patch(String body) {
        setMethod("PATCH");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> patch(JsonNode body) {
        setMethod("PATCH");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> patch(InputStream body) {
        setMethod("PATCH");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> patch(File body) {
        setMethod("PATCH");
        setBody(body);
        return execute();
    }

    //-------------------------------------------------------------------------
    // POST
    //-------------------------------------------------------------------------

    @Override
    public CompletionStage<? extends StandaloneWSResponse> post(String body) {
        setMethod("POST");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> post(JsonNode body) {
        setMethod("POST");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> post(InputStream body) {
        setMethod("POST");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> post(File body) {
        setMethod("POST");
        setBody(body);
        return execute();
    }

    //-------------------------------------------------------------------------
    // PUT
    //-------------------------------------------------------------------------

    @Override
    public CompletionStage<? extends StandaloneWSResponse> put(String body) {
        setMethod("PUT");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> put(JsonNode body) {
        setMethod("PUT");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> put(InputStream body) {
        setMethod("PUT");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> put(File body) {
        setMethod("PUT");
        setBody(body);
        return execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> delete() {
        return execute("DELETE");
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> head() {
        return execute("HEAD");
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> options() {
        return execute("OPTIONS");
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> execute(String method) {
        setMethod(method);
        return execute();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<? extends StandaloneWSResponse> execute() {
        WSRequestExecutor executor = foldRight(r -> {
            StandaloneAhcWSRequest ahcWsRequest = (StandaloneAhcWSRequest) r;
            Request ahcRequest = ahcWsRequest.buildRequest();
            return client.execute(ahcRequest);
        }, filters.iterator());
        return executor.apply(this);
    }

    private WSRequestExecutor foldRight(WSRequestExecutor executor, Iterator<WSRequestFilter> iterator) {
        if (! iterator.hasNext()) {
            return executor;
        }

        WSRequestFilter next = iterator.next();
        return foldRight(next.apply(executor), iterator);
    }

    @Override
    public CompletionStage<? extends StreamedResponse> stream() {
        return client.executeStream(buildRequest());
    }

    Request buildRequest() {
        boolean validate = true;
        HttpHeaders possiblyModifiedHeaders = new DefaultHttpHeaders(validate);
        this.headers.forEach(possiblyModifiedHeaders::add);

        RequestBuilder builder = new RequestBuilder(method);

        builder.setUrl(url);
        builder.setQueryParams(queryParameters);

        if (body == null) {
            // do nothing
        } else if (body instanceof String) {
            String stringBody = ((String) body);

            // Detect and maybe add charset
            String contentType = possiblyModifiedHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
            if (contentType == null) {
                contentType = "text/plain";
            }

            // Always replace the content type header to make sure exactly one exists
            List<String> contentTypeList = new ArrayList<>();
            contentTypeList.add(contentType);
            possiblyModifiedHeaders.set(HttpHeaders.Names.CONTENT_TYPE, contentTypeList);

            // Find a charset and try to pull a string out of it...
            Charset charset = HttpUtils.parseCharset(contentType);
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
            byte[] bodyBytes = stringBody.getBytes(charset);

            // If using a POST with OAuth signing, the builder looks at
            // getFormParams() rather than getBody() and constructs the signature
            // based on the form params.
            if (contentType.equals(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED) && calculator != null) {
                possiblyModifiedHeaders.remove(HttpHeaders.Names.CONTENT_LENGTH);

                Map<String, List<String>> stringListMap = FormUrlEncodedParser.parseAsJava(stringBody, "utf-8");
                stringListMap.forEach((key, values) -> values.forEach(value -> builder.addFormParam(key, value)));
            } else {
                builder.setBody(stringBody);
            }

            builder.setCharset(charset);
        } else if (body instanceof JsonNode) {
            JsonNode jsonBody = (JsonNode) body;
            List<String> contentType = new ArrayList<>();
            contentType.add("application/json");
            possiblyModifiedHeaders.set(HttpHeaders.Names.CONTENT_TYPE, contentType);
            byte[] bodyBytes;
            try {
                bodyBytes = objectMapper.writeValueAsBytes(jsonBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            builder.setBody(new ByteArrayBodyGenerator(bodyBytes));
        } else if (body instanceof File) {
            File fileBody = (File) body;
            FileBodyGenerator bodyGenerator = new FileBodyGenerator(fileBody);
            builder.setBody(bodyGenerator);
        } else if (body instanceof InputStream) {
            InputStream inputStreamBody = (InputStream) body;
            InputStreamBodyGenerator bodyGenerator = new InputStreamBodyGenerator(inputStreamBody);
            builder.setBody(bodyGenerator);
        } else if (body instanceof Source) {
            // If the body has a streaming interface it should be up to the user to provide a manual Content-Length
            // else every content would be Transfer-Encoding: chunked
            // If the Content-Length is -1 Async-Http-Client sets a Transfer-Encoding: chunked
            // If the Content-Length is great than -1 Async-Http-Client will use the correct Content-Length
            long contentLength = Optional.ofNullable(possiblyModifiedHeaders.get(HttpHeaders.Names.CONTENT_LENGTH))
                    .map(Long::valueOf).orElse(-1L);
            possiblyModifiedHeaders.remove(HttpHeaders.Names.CONTENT_LENGTH);

            @SuppressWarnings("unchecked") Source<ByteString, ?> sourceBody = (Source<ByteString, ?>) body;
            Publisher<ByteBuffer> publisher = sourceBody.map(ByteString::toByteBuffer)
                    .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);
            builder.setBody(publisher, contentLength);
        } else {
            throw new IllegalStateException("Impossible body: " + body);
        }

        builder.setHeaders(possiblyModifiedHeaders);

        if (this.timeout == -1 || this.timeout > 0) {
            builder.setRequestTimeout(this.timeout);
        }

        builder.setFollowRedirect(this.followRedirects);

        if (this.virtualHost != null) {
            builder.setVirtualHost(this.virtualHost);
        }

        if (this.username != null && this.password != null && this.scheme != null) {
            builder.setRealm(auth(this.username, this.password, this.scheme));
        }

        if (this.calculator != null) {
            if (this.calculator instanceof OAuth.OAuthCalculator) {
                SignatureCalculator calc = ((OAuth.OAuthCalculator) this.calculator).getCalculator();
                builder.setSignatureCalculator(calc);
            } else if (this.calculator instanceof SignatureCalculator) {
                SignatureCalculator calc = ((SignatureCalculator) this.calculator);
                builder.setSignatureCalculator(calc);
            } else {
                throw new IllegalStateException("Use OAuth.OAuthCalculator");
            }
        }

        return builder.build();
    }

    private void addValueTo(Map<String, List<String>> map, String name, String value) {
        if (map.containsKey(name)) {
            Collection<String> values = map.get(name);
            values.add(value);
        } else {
            List<String> values = new ArrayList<>();
            values.add(value);
            map.put(name, values);
        }
    }

    Realm auth(String username, String password, WSAuthScheme scheme) {
        Realm.AuthScheme authScheme = Realm.AuthScheme.valueOf(scheme.name());
        Boolean usePreemptiveAuth = !(this.scheme != null && this.scheme == WSAuthScheme.DIGEST);
        return (new Realm.Builder(username, password))
                .setScheme(authScheme)
                .setUsePreemptiveAuth(usePreemptiveAuth)
                .build();
    }
}
