/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.akkahttp;

import akka.actor.ActorSystem;
import akka.http.impl.model.parser.HeaderParser$;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
import akka.http.javadsl.model.headers.Cookie;
import akka.http.javadsl.model.headers.UserAgent;
import akka.http.scaladsl.model.headers.ProductVersion;
import akka.japi.Pair;
import akka.parboiled2.ParserInput$;
import akka.pattern.PatternsCS;
import akka.stream.Materializer;
import play.api.libs.ws.WSClientConfig;
import play.libs.ws.*;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class StandaloneAkkaHttpWSRequest implements StandaloneWSRequest {

  private final HttpRequest request;
  private final List<WSRequestFilter> filters;
  private final Duration timeout;

  private final ActorSystem sys;
  private final Materializer mat;
  private final HttpsConnectionContext ctx;
  private final WSClientConfig config;

  StandaloneAkkaHttpWSRequest(String url, ActorSystem sys, Materializer mat, HttpsConnectionContext ctx, WSClientConfig config) {
    this(HttpRequest.create(url), new ArrayList<>(), Duration.ZERO, sys, mat, ctx, config);
  }

  private StandaloneAkkaHttpWSRequest(HttpRequest request, List<WSRequestFilter> filters, Duration timeout, ActorSystem sys, Materializer mat, HttpsConnectionContext ctx, WSClientConfig config) {
    this.request = config.userAgent().fold(
      () -> request,
      (ua) -> request.addHeader(UserAgent.create(
        // FIXME JAVA API expose ProductVersion.parseMultiple in Java API
        scala.collection.JavaConverters.seqAsJavaList(ProductVersion.parseMultiple(ua)).toArray(new ProductVersion[0])
      ))
    );
    this.filters = filters;
    this.timeout = timeout;
    this.sys = sys;
    this.mat = mat;
    this.ctx = ctx;
    this.config = config;
  }

  /**
   * Perform a GET on the request asynchronously.
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> get() {
    return execute(HttpMethods.GET.value());
  }

  /**
   * Perform a PATCH on the request asynchronously.
   *
   * @param body the BodyWritable
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> patch(BodyWritable body) {
    return setBody(body).execute(HttpMethods.PATCH.value());
  }

  /**
   * Perform a POST on the request asynchronously.
   *
   * @param body the BodyWritable
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> post(BodyWritable body) {
    return setBody(body).execute(HttpMethods.POST.value());
  }

  /**
   * Perform a PUT on the request asynchronously.
   *
   * @param body the BodyWritable
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> put(BodyWritable body) {
    return setBody(body).execute(HttpMethods.PUT.value());
  }

  /**
   * Perform a DELETE on the request asynchronously.
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> delete() {
    return execute(HttpMethods.DELETE.value());
  }

  /**
   * Perform a HEAD on the request asynchronously.
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> head() {
    return execute(HttpMethods.HEAD.value());
  }

  /**
   * Perform an OPTIONS on the request asynchronously.
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> options() {
    return execute(HttpMethods.OPTIONS.value());
  }

  /**
   * Executes an arbitrary method on the request asynchronously.
   *
   * @param method The method to execute
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> execute(String method) {
    return setMethod(method).execute();
  }

  /**
   * Executes an arbitrary method on the request asynchronously.  Should be used with setMethod().
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> execute() {
    final WSRequestExecutor akkaExecutor = (request) -> {
      final CompletableFuture<StandaloneAkkaHttpWSResponse> resultFuture =
        Http.get(sys)
          .singleRequest(((StandaloneAkkaHttpWSRequest)request).request, ctx)
          .thenApply((r) -> new StandaloneAkkaHttpWSResponse(r, mat))
          .toCompletableFuture();

      if (timeout.equals(Duration.ZERO)) {
        return resultFuture.thenApply((response) -> (StandaloneWSResponse)response);
      }
      else {
        final CompletableFuture<StandaloneAkkaHttpWSResponse> timeoutException = new CompletableFuture<>();
        timeoutException.completeExceptionally(new TimeoutException("Request timeout after " + timeout));

        final CompletableFuture<StandaloneAkkaHttpWSResponse> timeoutFuture =
          PatternsCS.after(
            FiniteDuration.apply(timeout.toNanos(), TimeUnit.NANOSECONDS),
            sys.scheduler(), sys.dispatcher(),
            timeoutException
          ).toCompletableFuture();

        return CompletableFuture.anyOf(resultFuture, timeoutFuture).thenApply((response) -> (StandaloneWSResponse)response);
      }
    };

    return filters.stream()
      .reduce(akkaExecutor, (executor, filter) -> filter.apply(executor), (exec1, exec2) -> exec2)
      .apply(this);
  }

  /**
   * Executes this request and streams the response body.
   * <p>
   * Use {@code response.bodyAsSource()} with this method.
   *
   * @return a promise to the response
   */
  @Override
  public CompletionStage<? extends StandaloneWSResponse> stream() {
    return execute();
  }

  /**
   * Sets the HTTP method this request should use, where the no args execute() method is invoked.
   *
   * @param method the HTTP method.
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setMethod(String method) {
    return copy(request.withMethod(
      HttpMethods.lookup(method)
        .orElseThrow(() -> new IllegalArgumentException("Unknown HTTP method " + method))));
  }

  /**
   * Set the body this request should use.
   *
   * @param body the body of the request.
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setBody(BodyWritable body) {
    if (body instanceof InMemoryBodyWritable) {
      final InMemoryBodyWritable writable = (InMemoryBodyWritable)body;
      return copy(request.withEntity(
        HttpEntities.create(parseContentType(body.contentType()), writable.body().get())));
    }
    else if (body instanceof SourceBodyWritable) {
      final SourceBodyWritable writable = (SourceBodyWritable)body;
      return copy(request.withEntity(
        HttpEntities.create(parseContentType(body.contentType()), writable.body().get())));
    }
    else {
      throw new IllegalArgumentException("Unsupported BodyWritable: " + body);
    }
  }

  /**
   * Set headers to the request.  Note that duplicate headers are allowed
   * by the HTTP specification, and removing a header is not available
   * through this API. Any existing header will be discarded here.
   *
   * @param headers the headers
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setHeaders(Map<String, List<String>> headers) {
    // FIXME JAVA API no Java Api to replace headers on a request
    HttpRequest requestNoHeaders = request;
    for (HttpHeader h: request.getHeaders()) {
      requestNoHeaders = requestNoHeaders.removeHeader(h.name());
    }

    return copy(
      requestNoHeaders.addHeaders(
        headers.entrySet().stream().flatMap(
          (h) -> h.getValue().stream().map((v) -> parseHeader(h.getKey(), v))
        ).collect(Collectors.toList()))
    );
  }

  /**
   * Adds a header to the request.  Note that duplicate headers are allowed
   * by the HTTP specification, and removing a header is not available
   * through this API. Existent headers will be preserved.
   *
   * @param name  the header name
   * @param value the header value
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest addHeader(String name, String value) {
    return copy(request.addHeader(parseHeader(name, value)));
  }

  /**
   * Sets the query string to query.
   *
   * @param query the fully formed query string
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setQueryString(String query) {
    return copy(
      request.withUri(
        request.getUri().query(
          Query.create(query))));
  }

  /**
   * Adds a query parameter with the given name, this can be called repeatedly and will preserve existing values.
   * Duplicate query parameters are allowed.
   *
   * @param name  the query parameter name
   * @param value the query parameter value
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest addQueryParameter(String name, String value) {
    return copy(
      request.withUri(
        request.getUri().query(
          request.getUri().query().withParam(name, value))));
  }

  /**
   * Sets the query string parameters. This will discard existing values.
   *
   * @param params the query string parameters
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setQueryString(Map<String, List<String>> params) {
    return copy(
      request.withUri(
        request.getUri().query(
          Query.create(params.entrySet().stream().flatMap(
            (entry) -> entry.getValue().stream().map((value) -> Pair.create(entry.getKey(), value))
          ).collect(Collectors.toList())))));
  }

  /**
   * Add a new cookie. This can be called repeatedly and will preserve existing cookies.
   *
   * @param cookie the cookie to be added
   * @return the modified WSRequest.
   * @see #addCookies(WSCookie...)
   * @see #setCookies(List)
   */
  @Override
  public StandaloneWSRequest addCookie(WSCookie cookie) {
    return addCookies(cookie);
  }

  /**
   * Add new cookies. This can be called repeatedly and will preserve existing cookies.
   *
   * @param cookies the list of cookies to be added
   * @return the modified WSRequest.
   * @see #addCookie(WSCookie)
   * @see #setCookies(List)
   */
  @Override
  public StandaloneWSRequest addCookies(WSCookie... cookies) {
    return copy(request.addHeaders(
      Arrays.stream(cookies)
        .map((c) -> Cookie.create(c.getName(), c.getValue()))
        .collect(Collectors.toList())));
  }

  /**
   * Set the request cookies. This discard the existing cookies.
   *
   * @param cookies the cookies to be used.
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setCookies(List<WSCookie> cookies) {
    return copy(request.removeHeader("Cookie")).addCookies(cookies.toArray(new WSCookie[cookies.size()]));
  }

  /**
   * Sets the authentication header for the current request using BASIC authentication.
   *
   * @param userInfo a string formed as "username:password".
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setAuth(String userInfo) {
    final String[] credentials = userInfo.split(":");
    return setAuth(credentials[0], credentials[1]);
  }

  /**
   * Sets the authentication header for the current request using BASIC authentication.
   *
   * @param username the basic auth username
   * @param password the basic auth password
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setAuth(String username, String password) {
    return setAuth(username, password, WSAuthScheme.BASIC);
  }

  /**
   * Sets the authentication header for the current request.
   *
   * @param username the username
   * @param password the password
   * @param scheme   authentication scheme
   * @return the modified WSRequest.
   */
  @Override
  public StandaloneWSRequest setAuth(String username, String password, WSAuthScheme scheme) {
    if (scheme.equals(WSAuthScheme.BASIC)) {
      return copy(request.addCredentials(BasicHttpCredentials.createBasicHttpCredentials(username, password)));
    }
    else {
      throw new IllegalArgumentException("Authentication scheme [" + scheme + "] not yet supported");
    }
  }

  /**
   * Sets an (OAuth) signature calculator.
   *
   * @param calculator the signature calculator
   * @return the modified WSRequest
   */
  @Override
  public StandaloneWSRequest sign(WSSignatureCalculator calculator) {
    // FIXME https://github.com/playframework/play-ws/issues/207
    throw new UnsupportedOperationException("Implementation is missing");
  }

  /**
   * Sets whether redirects (301, 302) should be followed automatically.
   *
   * @param followRedirects true if the request should follow redirects
   * @return the modified WSRequest
   */
  @Override
  public StandaloneWSRequest setFollowRedirects(boolean followRedirects) {
    // FIXME https://github.com/playframework/play-ws/issues/207
    throw new UnsupportedOperationException("Implementation is missing");
  }

  /**
   * Sets the virtual host as a "hostname:port" string.
   *
   * @param virtualHost the virtual host
   * @return the modified WSRequest
   */
  @Override
  public StandaloneWSRequest setVirtualHost(String virtualHost) {
    // FIXME JAVA API missing Host.create(Authority) Java Api in Akka Http
    return copy(request.addHeader(akka.http.scaladsl.model.headers.Host.apply(
      akka.http.scaladsl.model.Uri.Authority$.MODULE$.parse(
        ParserInput$.MODULE$.apply(virtualHost),
        Charset.forName("UTF8"),
        akka.http.scaladsl.model.Uri$ParsingMode$Relaxed$.MODULE$))));
  }

  /**
   * Sets the request timeout duration. Java {@link Duration} class does not have a specific instance
   * to represent an infinite timeout, but according to the docs, in practice, you can somehow emulate
   * it:
   * <p>
   * <blockquote>
   * A physical duration could be of infinite length. For practicality, the duration is stored
   * with constraints similar to Instant. The duration uses nanosecond resolution with a maximum
   * value of the seconds that can be held in a long. This is greater than the current estimated
   * age of the universe.
   * </blockquote>
   * <p>
   * Play WS uses the convention of setting a duration with negative value to have an infinite timeout.
   * So you will have:
   * <p>
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
  @Override
  public StandaloneWSRequest setRequestTimeout(Duration timeout) {
    return copy(timeout);
  }

  /**
   * Adds a request filter.
   *
   * @param filter a transforming filter.
   * @return the modified request.
   */
  @Override
  public StandaloneWSRequest setRequestFilter(WSRequestFilter filter) {
    final List<WSRequestFilter> newFilters = new ArrayList<>(filters);
    newFilters.add(filter);
    return copy(newFilters);
  }

  /**
   * Set the content type.  If the request body is a String, and no charset parameter is included, then it will
   * default to UTF-8.
   *
   * @param contentType The content type
   * @return the modified WSRequest
   */
  @Override
  public StandaloneWSRequest setContentType(String contentType) {
    // FIXME JAVA API entity.withContentType is missing in Java Api of Akka Http
    return copy(request.withEntity(
      ((RequestEntity)((akka.http.scaladsl.model.HttpEntity)request.entity()).withContentType((akka.http.scaladsl.model.ContentType)parseContentType(contentType)))
    ));
  }

  /**
   * @return the URL of the request.  This has not passed through an internal request builder and so will not be signed.
   */
  @Override
  public String getUrl() {
    return request.getUri().query(Query.EMPTY).toString();
  }

  /**
   * @return the headers (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
   */
  @Override
  public Map<String, List<String>> getHeaders() {
    final Map<String, List<String>> headers = new HashMap<>();
    for (final HttpHeader header: request.getHeaders()) {
      if (headers.containsKey(header.name())) {
        headers.get(header.name()).add(header.value());
      }
      else {
        headers.put(header.name(), new ArrayList<>(Collections.singletonList(header.value())));
      }
    }
    return headers;
  }

  /**
   * Get all the values of header with the specified name. If there are no values for
   * the header with the specified name, than an empty List is returned.
   *
   * @param name the header name.
   * @return all the values for this header name.
   */
  @Override
  public List<String> getHeaderValues(String name) {
    return getHeaders().getOrDefault(name, new ArrayList<>());
  }

  /**
   * Get the value of the header with the specified name. If there are more than one values
   * for this header, the first value is returned. If there are no values, than an empty
   * Optional is returned.
   *
   * @param name the header name
   * @return the header value
   */
  @Override
  public Optional<String> getHeader(String name) {
    return getHeaderValues(name).stream().findFirst();
  }

  /**
   * @return the query parameters (a copy to prevent side-effects). This has not passed through an internal request builder and so will not be signed.
   */
  @Override
  public Map<String, List<String>> getQueryParameters() {
    return request.getUri().query().toMultiMap();
  }

  /**
   * @return the auth username, null if not an authenticated request.
   */
  @Override
  public String getUsername() {
    return request.getHeader(Authorization.class)
      .filter((a) -> a.credentials() instanceof BasicHttpCredentials)
      .map((a) -> (BasicHttpCredentials)a.credentials())
      .map(BasicHttpCredentials::username)
      .orElseGet(() -> null);
  }

  /**
   * @return the auth password, null if not an authenticated request
   */
  @Override
  public String getPassword() {
    return request.getHeader(Authorization.class)
      .filter((a) -> a.credentials() instanceof BasicHttpCredentials)
      .map((a) -> (BasicHttpCredentials)a.credentials())
      .map(BasicHttpCredentials::password)
      .orElseGet(() -> null);
  }

  /**
   * @return the auth scheme, null if not an authenticated request.
   */
  @Override
  public WSAuthScheme getScheme() {
    return request.getHeader(Authorization.class)
      .filter((a) -> a.credentials() instanceof BasicHttpCredentials)
      .map((a) -> WSAuthScheme.BASIC)
      .orElseGet(() -> null);
  }

  /**
   * @return the signature calculator (example: OAuth), null if none is set.
   */
  @Override
  public WSSignatureCalculator getCalculator() {
    // FIXME https://github.com/playframework/play-ws/issues/207
    throw new UnsupportedOperationException("Implementation is missing");
  }

  /**
   * Gets the original request timeout duration, passed into the request as input.
   *
   * @return the timeout duration.
   */
  @Override
  public Duration getRequestTimeoutDuration() {
    return timeout;
  }

  /**
   * @return true if the request is configure to follow redirect, false if it is configure not to, null if nothing is configured and the global client preference should be used instead.
   */
  @Override
  public boolean getFollowRedirects() {
    // FIXME https://github.com/playframework/play-ws/issues/207
    return false;
  }

  /**
   * @return the content type, if any, or null.
   */
  @Override
  public String getContentType() {
    // FIXME JAVA API no CotentTypes.NoContentType Java Api in Akka Http
    return request.entity().getContentType()
      .equals(akka.http.scaladsl.model.ContentTypes.NoContentType()) ? null : request.entity().getContentType().toString();
  }

  private HttpHeader parseHeader(String name, String value) {
    // FIXME JAVA API missing HttpHeader.parse Java API in Akka Http
    final akka.http.scaladsl.model.HttpHeader.ParsingResult result =
      akka.http.scaladsl.model.HttpHeader$.MODULE$.parse(name, value, HeaderParser$.MODULE$.DefaultSettings());

    if (result instanceof akka.http.scaladsl.model.HttpHeader$ParsingResult$Ok) {
      return ((akka.http.scaladsl.model.HttpHeader$ParsingResult$Ok)result).header();
    }
    else {
      throw new IllegalArgumentException("Unable to parse header [" + name + "] with value [" + value + "]");
    }
  }

  private ContentType parseContentType(String contentType) {
    // FIXME JAVA API missing ContentType.parse Java API in Akka Http
    final Either<scala.collection.immutable.List<akka.http.scaladsl.model.ErrorInfo>, akka.http.scaladsl.model.ContentType> contentTypeEither = akka.http.scaladsl.model.ContentType$.MODULE$.parse(contentType);
    if (contentTypeEither.isRight()) {
      return contentTypeEither.right().get();
    }
    else {
      throw new IllegalArgumentException("Unable to parse content type: " + contentType);
    }
  }

  private StandaloneWSRequest copy(HttpRequest request) {
    return new StandaloneAkkaHttpWSRequest(request, this.filters, this.timeout, this.sys, this.mat, this.ctx, this.config);
  }

  private StandaloneWSRequest copy(List<WSRequestFilter> filters) {
    return new StandaloneAkkaHttpWSRequest(this.request, filters, this.timeout, this.sys, this.mat, this.ctx, this.config);
  }

  private StandaloneWSRequest copy(Duration timeout) {
    return new StandaloneAkkaHttpWSRequest(this.request, this.filters, timeout, this.sys, this.mat, this.ctx, this.config);
  }

}
