/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.headers.SetCookie;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;
import play.libs.ws.WSCookieBuilder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class StandaloneAkkaHttpWSResponse implements StandaloneWSResponse {

  // FIXME make configurable
  private final static Duration UNMARSHAL_TIMEOUT = Duration.ofSeconds(1);

  private final HttpResponse response;
  private final Materializer mat;

  private HttpResponse strictResponse;

  StandaloneAkkaHttpWSResponse(HttpResponse response, Materializer mat) {
    this.response = response;
    this.mat = mat;
  }

  /**
   * @return all the headers from the response.
   */
  @Override
  public Map<String, List<String>> getHeaders() {
    final Map<String, List<String>> headers = new HashMap<>();
    for (final HttpHeader header: response.getHeaders()) {
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
   * @return the underlying implementation response object, if any.
   */
  @Override
  public Object getUnderlying() {
    return response;
  }

  /**
   * @return the HTTP status code from the response.
   */
  @Override
  public int getStatus() {
    return response.status().intValue();
  }

  /**
   * @return the text associated with the status code.
   */
  @Override
  public String getStatusText() {
    return response.status().reason();
  }

  /**
   * @return all the cookies from the response.
   */
  @Override
  public List<WSCookie> getCookies() {
    return StreamSupport.stream(response.getHeaders().spliterator(), false)
      .filter((h) -> h instanceof SetCookie)
      .map((h) -> {
        final SetCookie c = (SetCookie)h;
        return new WSCookieBuilder().setName(c.cookie().name()).setValue(c.cookie().value()).build();
      })
      .collect(Collectors.toList());
  }

  /**
   * @param name the cookie name
   * @return a single cookie from the response, if any.
   */
  @Override
  public Optional<WSCookie> getCookie(String name) {
    return getCookies().stream().filter((c) -> c.getName().equals(name)).findFirst();
  }

  /**
   * @return the content type.
   */
  @Override
  public String getContentType() {
    // FIXME JAVA API no CotentTypes.NoContentType Java Api in Akka Http
    return response.entity().getContentType()
      .equals(akka.http.scaladsl.model.ContentTypes.NoContentType()) ? null : response.entity().getContentType().toString();
  }

  /**
   * Returns the response getBody as a particular type, through a
   * {@link BodyReadable} transformation.  You can define your
   * own {@link BodyReadable} types:
   * <p>
   * <pre>
   * {@code public class MyClass {
   *   private BodyReadable<Foo, StandaloneWSResponse> fooReadable = (response) -> new Foo();
   *
   *   public void readAsFoo(StandaloneWSResponse response) {
   *       Foo foo = response.getBody(fooReadable);
   *   }
   * }
   * }
   * </pre>
   * <p>
   * or use {@code play.libs.ws.ahc.DefaultResponseReadables}
   * for the built-ins:
   * <p>
   * <pre>
   * {@code public class MyClass implements DefaultResponseReadables {
   *     public void readAsString(StandaloneWSResponse response) {
   *         String getBody = response.getBody(string());
   *     }
   *
   *     public void readAsJson(StandaloneWSResponse response) {
   *         JsonNode json = response.getBody(json());
   *     }
   * }
   * }
   * </pre>
   *
   * @param readable the readable to convert the response to a T
   * @return the response getBody transformed into an instance of T
   */
  @Override
  public <T> T getBody(BodyReadable<T> readable) {
    return readable.apply(this);
  }

  /**
   * The response body decoded as String, using a simple algorithm to guess the encoding.
   * <p>
   * This decodes the body to a string representation based on the following algorithm:
   * <p>
   * 1. Look for a "charset" parameter on the Content-Type. If it exists, set `charset` to its value and goto step 3.
   * 2. If the Content-Type is of type "text", set $charset to "ISO-8859-1"; else set `charset` to "UTF-8".
   * 3. Decode the raw bytes of the body using `charset`.
   * <p>
   * Note that this does not take into account any special cases for specific content types. For example, for
   * application/json, we do not support encoding autodetection and will trust the charset parameter if provided.
   *
   * @return the response body parsed as a String using the above algorithm.
   */
  @Override
  public String getBody() {
    return getBodyAsBytes().utf8String();
  }

  @Override
  public ByteString getBodyAsBytes() {
    try {
      // FIXME JAVA API no Unmarshalling Java API in Akka Http
      return getStrictResponse().entity().getDataBytes()
        .runWith(Sink.fold(ByteString.empty(), (b1, b2) -> b1.concat(b2)), mat)
        .toCompletableFuture()
        .get(UNMARSHAL_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Source<ByteString, ?> getBodyAsSource() {
    return response.entity().getDataBytes();
  }

  private synchronized HttpResponse getStrictResponse() {
    if (strictResponse != null) {
      return strictResponse;
    }
    else {
      try {
        final ResponseEntity strictEntity = response.entity()
          .toStrict(UNMARSHAL_TIMEOUT.toMillis(), mat)
          .toCompletableFuture()
          .get(UNMARSHAL_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        // FIXME JAVA API no toStrict Java API in Akka Http
        this.strictResponse = response.withEntity(strictEntity);
        return this.strictResponse;
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }
}
