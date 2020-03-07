/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import akka.stream.Materializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.reactivestreams.Publisher;
import play.api.libs.ws.ahc.FormUrlEncodedParser;
import play.libs.oauth.OAuth;
import play.libs.ws.*;
import play.shaded.ahc.io.netty.buffer.ByteBuf;
import play.shaded.ahc.io.netty.buffer.Unpooled;
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.io.netty.handler.codec.http.cookie.Cookie;
import play.shaded.ahc.io.netty.handler.codec.http.cookie.DefaultCookie;
import play.shaded.ahc.org.asynchttpclient.Realm;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.RequestBuilder;
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator;

import play.shaded.ahc.org.asynchttpclient.proxy.ProxyServer;
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyType;
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

import static java.util.Collections.singletonList;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

/**
 * Provides the User facing API for building a WS request.
 */
public class StandaloneAhcWSRequest implements StandaloneWSRequest {

    private static final Duration INFINITE = Duration.ofMillis(-1);

    private BodyWritable<?> bodyWritable;

    private String url;
    private String method = "GET";
    private final Map<String, List<String>> headers = new HashMap<>();
    private final Map<String, List<String>> queryParameters = new LinkedHashMap<>();

    private final List<WSCookie> cookies = new ArrayList<>();

    private WSAuthInfo auth;
    private WSSignatureCalculator calculator;
    private WSProxyServer proxyServer;

    private final StandaloneAhcWSClient client;

    private final Materializer materializer;

    private Duration timeout = Duration.ZERO;
    private Boolean followRedirects = null;
    private String virtualHost = null;

    private final List<WSRequestFilter> filters = new ArrayList<>();

    public StandaloneAhcWSRequest(StandaloneAhcWSClient client, String url, Materializer materializer) {
        this.client = client;
        try {
            // Per https://github.com/playframework/playframework/issues/7444
            // we should not allow the string to undergo URL decoding, which was
            // being done by URI.create, which expects a completely valid URI as
            // input.
            URL reference = new java.net.URL(url);

            this.url = url;
            this.materializer = materializer;
            this.bodyWritable = null;

            String userInfo = reference.getUserInfo();
            if (userInfo != null) {
                this.setAuth(userInfo);
            }
            if (reference.getQuery() != null) {
                this.setQueryString(reference.getQuery());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
        if (userInfo.isEmpty()) {
            throw new RuntimeException(new MalformedURLException("userInfo should not be empty"));
        }

        int split = userInfo.indexOf(':');

        if (split == 0) { // We only have a password without user
            this.auth = new WSAuthInfo("", userInfo.substring(1), WSAuthScheme.BASIC);
        } else if (split == -1) { // We only have a username without password
            this.auth = new WSAuthInfo(userInfo, "", WSAuthScheme.BASIC);
        } else {
            this.auth = new WSAuthInfo(
                userInfo.substring(0, split),
                userInfo.substring(split + 1),
                WSAuthScheme.BASIC
            );
        }

        return this;
    }

    @Override
    public StandaloneAhcWSRequest setAuth(WSAuthInfo auth) {
        this.auth = auth;
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
        return addHeader(CONTENT_TYPE.toString(), contentType);
    }

    @Override
    public StandaloneWSRequest setProxyServer(WSProxyServer proxyServer) {
        if (proxyServer == null) {
            throw new IllegalArgumentException("proxyServer must not be null.");
        }
        this.proxyServer = proxyServer;
        return this;
    }

    @Override
    public Optional<String> getContentType() {
        return getHeader(CONTENT_TYPE.toString());
    }

    @Override
    public StandaloneAhcWSRequest setUrl(String url) {
        this.url = url;
        return this;
    }

    @Override
    public StandaloneAhcWSRequest setMethod(String method) {
        this.method = method;
        return this;
    }

    /**
     * Sets a BodyWritable directly. See {@link DefaultBodyWritables} for common bodies.
     * Also sets a Content-Type header if it's not set on the request but specified in the BodyWritable.
     *
     * @param bodyWritable the bodyWritable to set.
     * @return the request with body
     */
    @Override
    public StandaloneAhcWSRequest setBody(BodyWritable bodyWritable) {
        this.bodyWritable = bodyWritable;

        String contentType = bodyWritable.contentType();
        if (contentType != null && headers.keySet().stream().noneMatch(s -> s.equalsIgnoreCase(CONTENT_TYPE.toString()))) {
            addHeader(HttpHeaderNames.CONTENT_TYPE.toString(), bodyWritable.contentType());
        }

        return this;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public List<WSCookie> getCookies() {
        return new ArrayList<>(cookies);
    }

    @Override
    public Optional<BodyWritable> getBody() {
        return Optional.ofNullable(this.bodyWritable);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return new HashMap<>(this.headers);
    }

    @Override
    public List<String> getHeaderValues(String name) {
        return getHeaders().getOrDefault(name, Collections.emptyList());
    }

    @Override
    public Optional<String> getHeader(String name) {
         return getHeaderValues(name).stream().findFirst();
    }

    @Override
    public Map<String, List<String>> getQueryParameters() {
        return new LinkedHashMap<>(this.queryParameters);
    }

    @Override
    public Optional<WSAuthInfo> getAuth() {
        return Optional.ofNullable(this.auth);
    }

    @Override
    public Optional<WSProxyServer> getProxyServer() {
        return Optional.ofNullable(this.proxyServer);
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

    @Override
    public CompletionStage<? extends StandaloneWSResponse> get() {
        return execute("GET");
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> patch(BodyWritable body) {
        return setMethod("PATCH").setBody(body).execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> post(BodyWritable body) {
        return setMethod("POST").setBody(body).execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> put(BodyWritable body) {
        return setMethod("PUT").setBody(body).execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> delete() {
        return setMethod("DELETE").execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> head() {
        return setMethod("HEAD").execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> options() {
        return setMethod("OPTIONS").execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> execute(String method) {
        return setMethod(method).execute();
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> execute() {
        WSRequestExecutor executor = foldRight(r -> {
            StandaloneAhcWSRequest ahcWsRequest = (StandaloneAhcWSRequest) r;
            Request ahcRequest = ahcWsRequest.buildRequest();
            return client.execute(ahcRequest);
        }, filters.iterator());
        return executor.apply(this);
    }

    @Override
    public CompletionStage<? extends StandaloneWSResponse> stream() {
        WSRequestExecutor executor = foldRight(r -> {
            StandaloneAhcWSRequest ahcWsRequest = (StandaloneAhcWSRequest) r;
            Request ahcRequest = ahcWsRequest.buildRequest();
            return client.executeStream(ahcRequest, materializer.executionContext());
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

    Request buildRequest() {
        final boolean validate = true;
        final HttpHeaders possiblyModifiedHeaders = new DefaultHttpHeaders(validate);
        this.headers.forEach(possiblyModifiedHeaders::add);

        RequestBuilder builder = new RequestBuilder(method);

        builder.setUrl(url);
        builder.setQueryParams(queryParameters);

        getBody().ifPresent(bodyWritable -> {
            // Detect and maybe add content type
            String contentType = possiblyModifiedHeaders.get(CONTENT_TYPE.toString());
            if (contentType == null) {
                contentType = bodyWritable.contentType();
            }

            // Always replace the content type header to make sure exactly one exists
            possiblyModifiedHeaders.set(CONTENT_TYPE.toString(), singletonList(contentType));

            if (bodyWritable instanceof InMemoryBodyWritable) {
                ByteString byteString = ((InMemoryBodyWritable) bodyWritable).body().get();
                if (contentType.equals("application/json")) {
                    // there is no applicable charset for JSON, per RFC 7159
                    // https://tools.ietf.org/html/rfc7159#section-8.1
                    builder.setBody(byteString.toArray());
                } else {
                    // Find a charset and try to pull a string out of it...
                    Charset charset = HttpUtils.extractContentTypeCharsetAttribute(contentType);
                    if (charset == null) {
                        charset = StandardCharsets.UTF_8;
                    }
                    builder.setCharset(charset);
                    String stringBody = byteString.decodeString(charset);

                    // If using a POST with OAuth signing, the builder looks at
                    // getFormParams() rather than getBody() and constructs the signature
                    // based on the form params.
                    if (contentType.equals(APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
                        possiblyModifiedHeaders.remove(CONTENT_LENGTH.toString());

                        // XXX shouldn't the encoding be same as charset?
                        Map<String, List<String>> stringListMap = FormUrlEncodedParser.parseAsJava(stringBody, "utf-8");
                        stringListMap.forEach((key, values) -> values.forEach(value -> builder.addFormParam(key, value)));
                    } else {
                        builder.setBody(stringBody);
                    }
                }
            } else if (bodyWritable instanceof SourceBodyWritable) {
                // If the bodyWritable has a streaming interface it should be up to the user to provide a manual Content-Length
                // else every content would be Transfer-Encoding: chunked
                // If the Content-Length is -1 Async-Http-Client sets a Transfer-Encoding: chunked
                // If the Content-Length is great than -1 Async-Http-Client will use the correct Content-Length
                long contentLength = Optional.ofNullable(possiblyModifiedHeaders.get(CONTENT_LENGTH.toString()))
                        .map(Long::valueOf).orElse(-1L);
                possiblyModifiedHeaders.remove(CONTENT_LENGTH.toString());

                @SuppressWarnings("unchecked") Source<ByteString, ?> sourceBody = ((SourceBodyWritable) bodyWritable).body().get();
                Publisher<ByteBuf> publisher = sourceBody.map(bs -> Unpooled.wrappedBuffer(bs.toByteBuffer()))
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);
                builder.setBody(publisher, contentLength);
            } else {
                throw new IllegalStateException("Unknown body writable: " + bodyWritable);
            }
        });

        builder.setHeaders(possiblyModifiedHeaders);

        if (this.timeout.isNegative()) {
            builder.setRequestTimeout(((int) INFINITE.toMillis()));
        } else if (this.timeout.compareTo(Duration.ZERO) > 0) {
            builder.setRequestTimeout(((int) this.timeout.toMillis()));
        }

        getFollowRedirects().ifPresent(builder::setFollowRedirect);

        getVirtualHost().ifPresent(builder::setVirtualHost);

        this.getAuth().ifPresent(auth -> builder.setRealm(auth(auth.getUsername(), auth.getPassword(), auth.getScheme())));

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
            Cookie ahcCookie = new DefaultCookie(cookie.getName(), cookie.getValue());
            ahcCookie.setWrap(false);
            ahcCookie.setDomain(cookie.getDomain().orElse(null));
            ahcCookie.setPath(cookie.getPath().orElse(null));
            ahcCookie.setMaxAge(cookie.getMaxAge().orElse(-1L));
            ahcCookie.setSecure(cookie.isSecure());
            ahcCookie.setHttpOnly(cookie.isHttpOnly());
            builder.addCookie(ahcCookie);
        });

        getProxyServer().ifPresent(ps -> builder.setProxyServer(createProxy(ps)));

        return builder.build();
    }

    private ProxyServer createProxy(WSProxyServer proxyServer) {
        String host = proxyServer.getHost();
        int port = proxyServer.getPort();
        ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(host, port);

        proxyServer.getPrincipal().ifPresent(principal -> {
            Realm.Builder realmBuilder = new Realm.Builder(principal, proxyServer.getPassword().orElse(null));
            String protocol = proxyServer.getProtocol().orElse("http").toLowerCase(Locale.ENGLISH);
            switch (protocol) {
                case "http":
                case "https":
                    realmBuilder.setScheme(Realm.AuthScheme.BASIC);
                case "kerberos":
                    realmBuilder.setScheme(Realm.AuthScheme.KERBEROS);
                case "ntlm":
                    realmBuilder.setScheme(Realm.AuthScheme.NTLM);
                case "spnego":
                    realmBuilder.setScheme(Realm.AuthScheme.SPNEGO);
                default:
                    // Default to BASIC rather than throwing an error.
                    realmBuilder.setScheme(Realm.AuthScheme.BASIC);
            }
            proxyServer.getEncoding().ifPresent(enc -> realmBuilder.setCharset(Charset.forName(enc)));
            proxyServer.getNtlmDomain().ifPresent(realmBuilder::setNtlmDomain);
            proxyBuilder.setRealm(realmBuilder);
        });

        String proxyType = proxyServer.getProxyType().orElse("http").toLowerCase(Locale.ENGLISH);
        switch (proxyType) {
            case "http":
                proxyBuilder.setProxyType(ProxyType.HTTP);
            case "socksv4":
                proxyBuilder.setProxyType(ProxyType.SOCKS_V4);
            case "socksv5":
                proxyBuilder.setProxyType(ProxyType.SOCKS_V5);
        }

        proxyServer.getNonProxyHosts().ifPresent(proxyBuilder::setNonProxyHosts);
        return proxyBuilder.build();
    }

    private static void addValueTo(Map<String, List<String>> map, String name, String value) {
        final Optional<String> existing = map.keySet().stream().filter(s -> s.equalsIgnoreCase(name)).findAny();
        if (existing.isPresent()) {
            existing.ifPresent(n -> {
                final List<String> oldValues = map.get(n);
                final List<String> newValues;
                if (oldValues == null) { // yep!  that's right...
                    newValues = Collections.singletonList(value);
                } else {
                    newValues = new ArrayList<>(oldValues.size() + 1);
                    newValues.addAll(oldValues);
                    newValues.add(value);
                }
                map.put(n, newValues);
            });
        } else {
            map.put(name, Collections.singletonList(value));
        }
    }

    static Realm auth(String username, String password, WSAuthScheme scheme) {
        Realm.AuthScheme authScheme = Realm.AuthScheme.valueOf(scheme.name());
        Boolean usePreemptiveAuth = scheme != WSAuthScheme.DIGEST;
        return (new Realm.Builder(username, password))
                .setScheme(authScheme)
                .setUsePreemptiveAuth(usePreemptiveAuth)
                .build();
    }
}
