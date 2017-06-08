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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Provides the User facing API for building a WS request.
 */
public class StandaloneAhcWSRequest implements StandaloneWSRequest {

    private static final Duration INFINITE = Duration.ofMillis(-1);

    private WSBody<Object> wsBody;

    private final String url;
    private String method = "GET";
    private final Map<String, List<String>> headers = new HashMap<>();
    private final Map<String, List<String>> queryParameters = new HashMap<>();

    private final List<WSCookie> cookies = new ArrayList<>();

    private String username;
    private String password;
    private WSAuthScheme scheme;
    private WSSignatureCalculator calculator;
    private final StandaloneAhcWSClient client;

    private final Materializer materializer;
    private final ObjectMapper objectMapper;

    private Duration timeout = Duration.ZERO;
    private Boolean followRedirects = null;
    private String virtualHost = null;

    private final List<WSRequestFilter> filters = new ArrayList<>();

    public StandaloneAhcWSRequest(StandaloneAhcWSClient client, String url, Materializer materializer, ObjectMapper mapper) {
        this.client = client;
        URI reference = URI.create(url);

        this.url = url;
        this.materializer = materializer;
        this.objectMapper = mapper;
        this.wsBody = AhcWSBody.empty();

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

    @Override
    public StandaloneAhcWSRequest addHeader(String name, String value) {
        addValueTo(headers, name, value);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setHeaders(Map<String, List<String>> headers) {
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setQueryString(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length > 2) {
                throw new RuntimeException(new MalformedURLException("QueryString parameter should not have more than 2 = per part"));
            } else if (keyValue.length == 2) {
                this.addQueryParameter(keyValue[0], keyValue[1]);
            } else if (keyValue.length == 1 && param.charAt(0) != '=') {
                this.addQueryParameter(keyValue[0], null);
            } else {
                throw new RuntimeException(new MalformedURLException("QueryString part should not start with an = and not be empty"));
            }
        }
        return this;
    }

    @Override
    public StandaloneAhcWSRequest addQueryParameter(String name, String value) {
        addValueTo(queryParameters, name, value);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setQueryString(Map<String, List<String>> params) {
        this.queryParameters.clear();
        this.queryParameters.putAll(params);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest addCookie(WSCookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("Trying to add a null WSCookie");
        }

        this.cookies.add(cookie);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest addCookies(WSCookie... cookies) {
        Arrays.asList(cookies).forEach(this::addCookie);
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setCookies(List<WSCookie> cookies) {
        this.cookies.clear();
        cookies.forEach(this::addCookie);
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
    public StandaloneAhcWSRequest setRequestTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("Timeout must not be null.");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setContentType(String contentType) {
        return addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
    }

    @Override
    public Optional<String> getContentType() {
        final List<String> values = headers.get(HttpHeaders.Names.CONTENT_BASE);
        if (values == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(values.get(0));
        }
    }

    @Override
    public StandaloneAhcWSRequest setMethod(String method) {
        this.method = method;
        return this;
    }

    /**
     * Returns the wsBody of the request.
     *
     * @return
     */
    public WSBody body() {
        return wsBody;
    }

    /**
     * Sets a wsBody directly.
     *
     * @param body the wsBody as an unbound object.
     * @return the wsBody directly
     */
    @Override
    public StandaloneAhcWSRequest setBody(WSBody body) {
        if (body == null) {
            this.wsBody = AhcWSBody.empty();
        } else {
            this.wsBody = body;
        }
        return this;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return new HashMap<>(this.headers);
    }

    @Override
    public Map<String, List<String>> getQueryParameters() {
        return new HashMap<>(this.queryParameters);
    }

    @Override
    public Optional<String> getUsername() {
        return Optional.ofNullable(this.username);
    }

    @Override
    public Optional<String> getPassword() {
        return Optional.ofNullable(this.password);
    }

    @Override
    public Optional<WSAuthScheme> getScheme() {
        return Optional.ofNullable(this.scheme);
    }

    @Override
    public Optional<WSSignatureCalculator> getCalculator() {
        return Optional.ofNullable(this.calculator);
    }

    @Override
    public Optional<Duration> getRequestTimeout() {
        return Optional.ofNullable(this.timeout);
    }

    @Override
    public Optional<Boolean> getFollowRedirects() {
        return Optional.ofNullable(this.followRedirects);
    }

    // Intentionally package public.
    Optional<String> getVirtualHost() {
        return Optional.ofNullable(this.virtualHost);
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
        if (!iterator.hasNext()) {
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

        Object body = wsBody.body();
        if (body == null) {
            // do nothing
        } else if (body instanceof String) {
            String stringBody = (String) body;

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
            InputStream inputStreamBody = ((InputStream) body);
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
            throw new IllegalStateException("Unknown body: " + wsBody);
        }

        builder.setHeaders(possiblyModifiedHeaders);

        if (this.timeout.isNegative()) {
            builder.setRequestTimeout(((int) INFINITE.toMillis()));
        } else if (this.timeout.compareTo(Duration.ZERO) > 0) {
            builder.setRequestTimeout(((int) this.timeout.toMillis()));
        }

        getFollowRedirects().map(builder::setFollowRedirect);

        getVirtualHost().map(builder::setVirtualHost);

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

        // add cookies
        this.cookies.forEach(cookie -> {
            AhcWSCookie ahcWSCookie = (AhcWSCookie) cookie;
            builder.addCookie(ahcWSCookie.getUnderlying());
        });

        return builder.build();
    }

    private void addValueTo(Map<String, List<String>> map, String name, String value) {
        if (map.containsKey(name)) {
            List<String> values = map.get(name);
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
