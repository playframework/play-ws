# Play WS Standalone

[![Twitter Follow](https://img.shields.io/twitter/follow/playframework?label=follow&style=flat&logo=twitter&color=brightgreen)](https://twitter.com/playframework)
[![Discord](https://img.shields.io/discord/931647755942776882?logo=discord&logoColor=white)](https://discord.gg/g5s2vtZ4Fa)
[![GitHub Discussions](https://img.shields.io/github/discussions/playframework/playframework?&logo=github&color=brightgreen)](https://github.com/playframework/playframework/discussions)
[![StackOverflow](https://img.shields.io/static/v1?label=stackoverflow&logo=stackoverflow&logoColor=fe7a16&color=brightgreen&message=playframework)](https://stackoverflow.com/tags/playframework)
[![YouTube](https://img.shields.io/youtube/channel/views/UCRp6QDm5SDjbIuisUpxV9cg?label=watch&logo=youtube&style=flat&color=brightgreen&logoColor=ff0000)](https://www.youtube.com/channel/UCRp6QDm5SDjbIuisUpxV9cg)
[![Twitch Status](https://img.shields.io/twitch/status/playframework?logo=twitch&logoColor=white&color=brightgreen&label=live%20stream)](https://www.twitch.tv/playframework)
[![OpenCollective](https://img.shields.io/opencollective/all/playframework?label=financial%20contributors&logo=open-collective)](https://opencollective.com/playframework)

[![Build Status](https://github.com/playframework/play-ws/actions/workflows/build-test.yml/badge.svg)](https://github.com/playframework/play-ws/actions/workflows/build-test.yml)
[![Maven](https://img.shields.io/maven-central/v/org.playframework/play-ws-standalone_2.13.svg?logo=apache-maven)](https://mvnrepository.com/artifact/org.playframework/play-ws-standalone_2.13)
[![Javadocs](https://javadoc.io/badge/org.playframework/play-ws-standalone_2.13.svg)](https://javadoc.io/doc/org.playframework/play-ws-standalone_2.13)
[![Repository size](https://img.shields.io/github/repo-size/playframework/play-ws.svg?logo=git)](https://github.com/playframework/play-ws)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://api.mergify.com/v1/badges/playframework/play-ws&style=flat)](https://mergify.com)

Play WS is a powerful HTTP Client library, originally developed by the Play team for use with Play Framework. It uses AsyncHttpClient for HTTP client functionality and has no Play dependencies.

We've provided some documentation here on how to use Play WS in your app (without Play). For more information on how to use Play WS in Play, please refer to the Play documentation.

## Getting Started

To get started, you can add `play-ahc-ws-standalone` as a dependency in SBT:

```scala
libraryDependencies += "org.playframework" %% "play-ahc-ws-standalone" % "LATEST_VERSION"

// Before version 3.0.0:
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "LATEST_VERSION"
```

Where you replace `LATEST_VERSION` with the version shown in this image: [![Latest released version](https://img.shields.io/maven-central/v/org.playframework/play-ws-standalone_2.13.svg)](http://mvnrepository.com/artifact/org.playframework/play-ws-standalone_2.13).

This adds the standalone version of Play WS, backed by [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client).  This library contains both the Scala and Java APIs, under `play.api.libs.ws` and `play.libs.ws`.

To add XML and JSON support using Play-JSON or Scala XML, add the following:

```scala
libraryDependencies += "org.playframework" %% "play-ws-standalone-xml" % playWsStandaloneVersion
libraryDependencies += "org.playframework" %% "play-ws-standalone-json" % playWsStandaloneVersion

// Before version 3.0.0:
libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-xml" % playWsStandaloneVersion
libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion
```

## Shading

Play WS uses shaded versions of AsyncHttpClient and OAuth Signpost, repackaged under the `play.shaded.ahc` and `play.shaded.oauth` package names, respectively.  Shading AsyncHttpClient means that the version of Netty used behind AsyncHttpClient is completely independent of the application and Play as a whole.

Specifically, shading AsyncHttpClient means that its Netty version does not conflict with the Netty version used by the application.

> **NOTE**: If you are developing play-ws and publishing `shaded-asynchttpclient` and `shaded-oauth` using `sbt publishLocal`, you need to be aware that updating `~/.ivy2/local` does not overwrite `~/.ivy2/cache` and so you will not see your updated shaded code until you remove it from cache.  See http://eed3si9n.com/field-test for more details.  This bug has been filed as https://github.com/sbt/sbt/issues/2687.

### AHC 3 migration notes

Play WS 3.1 uses shaded AsyncHttpClient 3.0.14. Most Play WS request, response, streaming, and OAuth entry points remain available, but redirect, cookie, and streamed-body behavior is not identical to the AHC 2-backed releases. Code that uses shaded AHC types directly through an `underlying` escape hatch must be updated for AHC 3. A client supplied directly as an `AsyncHttpClient` also uses that client's own defaults rather than all of Play WS's configuration defaults.

There are two additional compatibility changes for APIs that exposed AHC 2 implementation details:

* `play.api.libs.ws.ahc.DefaultStreamedAsyncHandler` no longer extends AHC 2's removed `StreamedAsyncHandler`, and its `onStream` method is gone. Use `StandaloneWSRequest.stream()` and consume `StandaloneWSResponse.bodyAsSource`; custom AHC handlers must use AHC 3's response-body callbacks.
* `play.libs.oauth.OAuth.OAuthCalculator.getCalculator()` now returns AHC's `SignatureCalculator` interface because AHC 3 removed the concrete `OAuthSignatureCalculator` type. OAuth 1 request signing through Play WS remains supported.

`AhcWSClientConfig` retains its Play WS 3.0 constructor, companion `apply`, `copy`, and 12-field extractor. New AHC 3 settings are available through their named accessors and `copy` parameters but are intentionally not added to the extractor. Code using the generated `tupled` or `curried` helpers, or treating the companion as a `Function12`, must be updated for the expanded configuration.

Play WS adapts `Source`, `File`, and input-stream-supplier request bodies to a one-shot streamed body. A redirect or retry that would replay one now fails explicitly instead of attempting a second subscription; a supplier is not automatically called again. In-memory bodies can be replayed. This differs from native AHC `File` and some native `InputStream` bodies, which have their own replay support.

AHC 3.0.14 preserves the method and body of non-`POST` requests across 301 and 302 redirects; the legacy rewrite to a bodyless `GET` now applies only to `POST`. A 303 still switches to a bodyless `GET`, except that `HEAD` and `OPTIONS` keep their method, while 307 and 308 preserve method and body. This can turn a former bodyless-`GET` success into an explicit replay failure for a one-shot Play WS request body. `QUERY` follows the same non-`POST` rule.

Cookie handling also differs from the AHC 2-based Play WS. Cookies added through the Play WS cookie API (`addCookies`, `withCookies`) now follow same-origin redirects, whereas AHC 2 dropped them on every redirect; cross-origin redirects still strip them. A raw `Cookie` header is now merged with API and cookie-store cookies on the initial request and on every redirect, and a raw pair wins over an API cookie of the same name. AHC 2 instead let API cookies replace the raw header on the initial request, sent only the raw header on redirects, and, with a cookie store, replaced the raw header with the stored cookies for the URL. Several raw `Cookie` headers are now folded into one. With the opt-in `play.ws.ahc.useCookieStore = true`, redirects and authentication retries re-encode the raw header: pairs can be reordered, valueless or unbalanced-quote pairs are dropped, and a value that AHC's strict encoder rejects fails the redirect or retry even though the initial request was sent. Prefer valid cookie syntax and the Play WS cookie API; `useLaxCookieEncoder = true` changes validation more broadly and is not a general safety workaround. The cookie store remains disabled by default.

Connections authenticated with NTLM, SPNEGO or Kerberos, or through a proxy login that applies to the whole connection, are now pooled per principal, so AHC no longer reuses them for requests made with other credentials.

AHC 3.0.14 also offers two opt-in redirect refusals. Set `play.ws.ahc.refuseSchemeDowngradeOnRedirect = true` to reject an HTTPS-to-HTTP hop, or `play.ws.ahc.refuseCrossOriginBodyOnRedirect = true` to reject a hop that would resend a request body to another origin. Without the latter, AHC follows such a redirect and resends the body. A hop that only moves the same host from `http` to `https`, keeping the port or using both schemes' default ports, is not refused as cross-origin. These settings are separate from credential stripping: AHC removes credentials such as `Authorization` and caller cookies from cross-origin redirects regardless, while the refusals decide whether the request body or an insecure hop is allowed at all. Both Play WS settings are unset by default, preserving AHC's defaults and its shaded system-property settings. A refused redirect does not contact the target; the failed request exposes AHC's shaded `play.shaded.ahc.org.asynchttpclient.handler.RedirectRefusedException` in its failure chain. If the origin replies before an upload finishes, an earlier write failure may surface instead. Neither setting silently converts a body-bearing request into an empty one.

Play WS now creates TLS connections through an engine factory built from its configured `SSLContext`, except with `play.ws.ssl.loose.acceptAnyCertificate = true`. `play.ws.ssl.enabledProtocols` and `play.ws.ssl.enabledCipherSuites` therefore take effect; earlier versions ignored them, so a handshake could use a TLS version or cipher suite outside the configured lists. A list that leaves nothing enabled in the SSL context, such as `enabledProtocols = [TLSv1.1]` on a JVM that disables TLS 1.1, now fails when the client is created. Because the factory reads the enabled protocols and cipher suites from the final AHC configuration, `AhcConfigBuilder.modifyUnderlying` can change them. A Netty `SslContext` set there is still used as is with `loose.acceptAnyCertificate = true`, as before; it then decides the protocols, cipher suites and certificates itself. Otherwise Play WS ignores it and logs a warning. To take over TLS completely, set an `SslEngineFactory` there instead, which then also becomes responsible for hostname verification.

Play WS defaults `play.ws.ahc.http2Enabled` to `false` to preserve HTTP/1.1 behavior. Its standard configured SSL engine factory does not offer HTTP/2 through ALPN, so setting this option to `true` alone does **not** enable HTTP/2 for HTTPS requests. A separately supplied AHC client may use a different TLS factory and defaults. Cleartext HTTP/2 (h2c) can be enabled explicitly with both `http2Enabled = true` and AHC's `http2CleartextEnabled` through `AhcConfigBuilder.modifyUnderlying`. For actual HTTP/2 connections, `http2InitialWindowSize` limits the bytes a server can send to one suspended stream, while `http2MaxConcurrentStreams` limits the contributing streams per connection. These limits count bytes received on the wire: with automatic decompression, a compressed response can inflate into far more decoded body data than its window (see `maxDecompressedResponseSize` below). Play WS requires `http2InitialWindowSize` to be greater than zero because a zero initial window can prevent a response stream from making initial progress.

The AHC defaults are a 16 MiB per-stream window and no client-side concurrent-stream cap (`http2MaxConcurrentStreams = -1` leaves the limit server-controlled). Play WS also defaults its connection limits to unlimited, so there is no hard client-side aggregate buffering bound when HTTP/2 is enabled. A rough upper estimate for received data queued by suspended HTTP/2 responses is `connections × effective concurrent streams × http2InitialWindowSize`, excluding network and decoder overhead; decoded data from compressed responses can be much larger. A finite aggregate policy needs limits on all three factors, but connection caps also apply to HTTP/1.1 hosts: with AHC's default zero wait for a free connection, requests exceeding a cap fail immediately. Do not apply a low `maxConnectionsPerHost` merely as an HTTP/2 memory setting without assessing HTTP/1.1 concurrency.

Set `play.ws.ahc.maxDecompressedResponseSize` to apply one decompressed-response limit to both HTTP/1.1 and HTTP/2. AHC's defaults apply when it is unset. This limits decoded bytes per response, not total client heap use: each socket read of a compressed body (up to 64 KiB) is inflated in full before downstream demand is checked, so a highly compressible response can queue many MiB of body parts beyond demand, roughly 64 KiB times the compression ratio per read. AHC 2 behaves the same way. AHC inflates any `Content-Encoding` a server sends, even when Play WS did not request compression, so assess memory use for clients that stream responses from untrusted servers. Setting the limit to zero disables decompression-bomb protection and is not recommended for untrusted responses.

`play.ws.ahc.shutdownQuietPeriod` and `play.ws.ahc.shutdownTimeout` control AHC event-loop shutdown. Both must be non-negative, and the timeout must be at least as long as the quiet period. Both default to zero to preserve Play WS's existing immediate-shutdown behavior.

AHC 3 no longer needs `netty-reactive-streams`; Play WS still uses Reactive Streams interfaces to connect AHC response callbacks to Pekko Streams. The old AHC 2.16.1 dependency in this repository is test-only, for differential OAuth signatures, and is not part of the published Play WS client.

The AHC 3.0.14 release includes fixes for [GHSA-v2j5-22fr-j62r](https://github.com/AsyncHttpClient/async-http-client/security/advisories/GHSA-v2j5-22fr-j62r), [GHSA-qjr7-w8pj-pmv9](https://github.com/AsyncHttpClient/async-http-client/security/advisories/GHSA-qjr7-w8pj-pmv9), [GHSA-x8v2-478q-2hvg](https://github.com/AsyncHttpClient/async-http-client/security/advisories/GHSA-x8v2-478q-2hvg), [GHSA-p2jm-6hj6-9rjg](https://github.com/AsyncHttpClient/async-http-client/security/advisories/GHSA-p2jm-6hj6-9rjg), and [GHSA-2jwh-9rmr-j4xf](https://github.com/AsyncHttpClient/async-http-client/security/advisories/GHSA-2jwh-9rmr-j4xf). Whether an older Play WS application is affected by each one depends on its configuration and requests.

### Shaded AHC Defaults 

Because Play WS shades AsyncHttpClient, the default settings are also shaded and so do not adhere to the AHC documentation.  This means that the settings in `ahc-default.properties` and the AsyncHttpClient system properties are prepended with `play.shaded.ahc`, for example the `usePooledMemory` setting in the shaded version of AsyncHttpClient is defined like this:

```properties
play.shaded.ahc.org.asynchttpclient.usePooledMemory=true
```

### Typed Bodies

The type system in Play-WS has changed so that the request body and the response body can use richer types.

You can define your own BodyWritable or BodyReadable, but if you want to use the default out of the box settings, you can import the type mappings with the DefaultBodyReadables / DefaultBodyWritables.

#### Scala

```scala
import play.api.libs.ws.DefaultBodyReadables._
import play.api.libs.ws.DefaultBodyWritables._
```

More likely you will want the XML and JSON support:

```scala
import play.api.libs.ws.XMLBodyReadables._
import play.api.libs.ws.XMLBodyWritables._
```

or

```scala
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
```

To use a BodyReadable in a response, you must type the response explicitly:

```scala
import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.XMLBodyReadables._ // required

def handleXml(ws: StandaloneWSClient)(
  implicit ec: ExecutionContext): Future[scala.xml.Elem] =
  ws.url("...").get().map { response =>
    response.body[scala.xml.Elem]
  }
```

or using Play-JSON:

```scala
import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.JsValue
import play.api.libs.ws.StandaloneWSClient

import play.api.libs.ws.JsonBodyReadables._ // required

def handleJsonResp(ws: StandaloneWSClient)(
  implicit ec: ExecutionContext): Future[JsValue] =
  ws.url("...").get().map { response =>
    response.body[JsValue]
  }
```

Note that there is a special case: when you are streaming the response, then you should get the body as a Source:

```scala
import scala.concurrent.ExecutionContext
import org.apache.pekko.util.ByteString
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.ws.StandaloneWSClient

def useWSStream(ws: StandaloneWSClient)(implicit ec: ExecutionContext) =
  ws.url("...").stream().map { response =>
     val source: Source[ByteString, _] = response.bodyAsSource
     val _ = source // do something with source
  }
```

To POST, you should pass in a type which has an implicit class mapping of BodyWritable:

```scala
import scala.concurrent.ExecutionContext
import play.api.libs.ws.DefaultBodyWritables._ // required

def postExampleString(ws: play.api.libs.ws.StandaloneWSClient)(
  implicit ec: ExecutionContext) = {
  val stringData = "Hello world"
  ws.url("...").post(stringData).map { response => /* do something */ }
}
```

To perform a QUERY request ([RFC 10008](https://www.rfc-editor.org/rfc/rfc10008)), which is safe and idempotent like GET but carries a request body:

```scala
import scala.concurrent.ExecutionContext
import play.api.libs.ws.DefaultBodyWritables._ // required

def queryExample(ws: play.api.libs.ws.StandaloneWSClient)(
  implicit ec: ExecutionContext) = {
  val queryBody = """{"search": "play framework"}"""
  ws.url("...")
    .addHttpHeaders("Content-Type" -> "application/json")
    .query(queryBody)
    .map { response => /* do something */ }
}
```

You can also define your own custom BodyReadable: 

```scala
import play.api.libs.ws.BodyReadable
import play.api.libs.ws.ahc.StandaloneAhcWSResponse

case class Foo(body: String)

implicit val fooBodyReadable = BodyReadable[Foo] { response =>
  import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }
  val ahcResponse = response.asInstanceOf[StandaloneAhcWSResponse].underlying[AHCResponse]
  Foo(ahcResponse.getResponseBody)
}
```

or custom BodyWritable:

```scala
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{ BodyWritable, InMemoryBody }

implicit val writeableOf_Foo: BodyWritable[Foo] = {
  // https://tools.ietf.org/html/rfc6838#section-3.2
  BodyWritable(foo => InMemoryBody(ByteString.fromString(foo.toString)), "application/vnd.company.category+foo")
}
```

#### Java

To use the default type mappings in Java, you should use the following:

```java
import play.libs.ws.DefaultBodyReadables;
import play.libs.ws.DefaultBodyWritables;
```

followed by:

```java
public class MyClient implements DefaultBodyWritables, DefaultBodyReadables {    
    public CompletionStage<String> doStuff() {
      return client.url("http://example.com").post(body("hello world")).thenApply(response ->
        response.body(string())
      );
    }
}
```

Note that there is a special case: when you are using a stream, then you should get the body as a Source:

```java

class MyClass {
    public CompletionStage<Source<ByteString, NotUsed>> readResponseAsStream() {
        return ws.url(url).stream().thenApply(response ->
            response.bodyAsSource()
        );
    }
}
```

You can also post a Source:

```java
class MyClass {
    public CompletionStage<String> doStuff() {
        Source<ByteString, NotUsed> source = fromSource();
        return ws.url(url).post(body(source)).thenApply(response ->
            response.body()
        );
    }
}
```

To perform a QUERY request ([RFC 10008](https://www.rfc-editor.org/rfc/rfc10008)), which is safe and idempotent like GET but carries a request body:

```java
public class MyClient implements DefaultBodyWritables, DefaultBodyReadables {
    public CompletionStage<String> doQuery() {
        return client.url("http://example.com")
            .query(body("{\"search\": \"play framework\"}", "application/json"))
            .thenApply(response ->
                response.body(string())
            );
    }
}
```

You can define a custom `BodyReadable`:

```java
import play.libs.ws.ahc.*;
import play.shaded.ahc.org.asynchttpclient.Response;

class FooReadable implements BodyReadable<StandaloneWSResponse, Foo> {
    public Foo apply(StandaloneWSResponse response) {
        Response ahcResponse = (Response) response.getUnderlying();
        return Foo.serialize(ahcResponse.getResponseBody(StandardCharsets.UTF_8));
    }
}
```

You can also define your own custom `BodyWritable`:

```java
public class MyClient {
    private BodyWritable<String> someOtherMethod(String string) {
        org.apache.pekko.util.ByteString byteString = org.apache.pekko.util.ByteString.fromString(string);
      return new DefaultBodyWritables.InMemoryBodyWritable(byteString, "text/plain");
    }
}
```

## Instantiating a standalone client

The standalone client needs [Pekko](https://pekko.apache.org/) to handle streaming data internally:

### Scala

In Scala, the way to call out to a web service and close down the client:

```scala
package playwsclient

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import play.api.libs.ws._
import play.api.libs.ws.ahc._

import scala.concurrent.Future

object ScalaClient {
  import DefaultBodyReadables._
  import scala.concurrent.ExecutionContext.Implicits._

  def main(args: Array[String]): Unit = {
    // Create Pekko system for thread and streaming management
    implicit val system = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }

    implicit val materializer = SystemMaterializer(system).materializer

    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
    val wsClient = StandaloneAhcWSClient()

    call(wsClient)
      .andThen { case _ => wsClient.close() }
      .andThen { case _ => system.terminate() }
  }

  def call(wsClient: StandaloneWSClient): Future[Unit] = {
    wsClient.url("http://www.google.com").get().map { response =>
      val statusText: String = response.statusText
      val body = response.body[String]
      println(s"Got a response $statusText: $body")
    }
  }
}
```

You can also create the standalone client directly from an AsyncHttpClient instance:

```scala
object ScalaClient {
  def main(args: Array[String]): Unit = {
    // Use 
    import java.time.Duration
    import play.shaded.ahc.org.asynchttpclient._
    val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
      .setMaxRequestRetry(0)
      .setShutdownQuietPeriod(Duration.ZERO)
      .setShutdownTimeout(Duration.ZERO).build
    val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
    val wsClient = new StandaloneAhcWSClient(asyncHttpClient)
    /// ...
  }
}
```

This is useful when there is an AsyncHttpClient configuration option that is not available in the WS config layer.

### Java

In Java the API is much the same:

```java
package playwsclient;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.*;
import com.typesafe.config.ConfigFactory;

import play.libs.ws.*;
import play.libs.ws.ahc.*;

public class JavaClient implements DefaultBodyReadables {
    private final StandaloneAhcWSClient client;
    private final ActorSystem system;

    public static void main(String[] args) {
        // Set up Pekko materializer to handle streaming
        final String name = "wsclient";
        ActorSystem system = ActorSystem.create(name);
        system.registerOnTermination(() -> System.exit(0));
        Materializer materializer = SystemMaterializer.get(system).materializer();

        // Create the WS client from the `application.conf` file, the current classloader and materializer.
        StandaloneAhcWSClient ws = StandaloneAhcWSClient.create(
                AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), system.getClass().getClassLoader()),
                materializer
        );

        JavaClient javaClient = new JavaClient(system, ws);
        javaClient.run();
    }

    JavaClient(ActorSystem system, StandaloneAhcWSClient client) {
        this.system = system;
        this.client = client;
    }

    public void run() {
        client.url("http://www.google.com").get()
                .whenComplete((response, throwable) -> {
                    String statusText = response.getStatusText();
                    String body = response.getBody(string());
                    System.out.println("Got a response " + statusText);
                })
                .thenRun(() -> {
                    try {
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .thenRun(system::terminate);
    }
}
```

Likewise, you can provide the AsyncHttpClient client explicitly from configuration:

```java
import java.time.Duration;

public class JavaClient implements DefaultBodyReadables {
     public static void main(String[] args) { 
        // ...
        // Set up AsyncHttpClient directly from config
        AsyncHttpClientConfig asyncHttpClientConfig =
            new DefaultAsyncHttpClientConfig.Builder()
                .setMaxRequestRetry(0)
                .setShutdownQuietPeriod(Duration.ZERO)
                .setShutdownTimeout(Duration.ZERO)
                .build();
        AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);
    
        // Set up WSClient instance directly from asynchttpclient.
        WSClient client = new AhcWSClient(asyncHttpClient, materializer);
        // ...
    }
}
```

## Caching

Play WS implements [HTTP Caching](https://tools.ietf.org/html/rfc7234) through CachingAsyncHttpClient, AhcHTTPCache and [CacheControl](https://github.com/playframework/cachecontrol), a minimal HTTP cache management library in Scala.

To create a standalone AHC client that uses caching, pass in an instance of AhcHttpCache with a cache adapter to the underlying implementation.  For example, to use Caffeine as the underlying cache, you could use the following:

```scala
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import com.github.benmanes.caffeine.cache.{ Caffeine, Ticker }

import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.ahc.cache.{
  AhcHttpCache, Cache, EffectiveURIKey, ResponseEntry
}

class CaffeineHttpCache extends Cache {
  val underlying = Caffeine.newBuilder()
    .ticker(Ticker.systemTicker())
    .expireAfterWrite(365, TimeUnit.DAYS)
    .build[EffectiveURIKey, ResponseEntry]()

  def remove(key: EffectiveURIKey) =
    Future.successful(Option(underlying.invalidate(key)))

  def put(key: EffectiveURIKey, entry: ResponseEntry) =
    Future.successful(underlying.put(key, entry))

  def get(key: EffectiveURIKey) =
    Future.successful(Option(underlying getIfPresent key ))

  def close(): Unit = underlying.cleanUp()
}

def withCache(implicit m: org.apache.pekko.stream.Materializer): StandaloneAhcWSClient = {
  implicit def ec = m.executionContext

  val cache = new CaffeineHttpCache()
  StandaloneAhcWSClient(httpCache = Some(new AhcHttpCache(cache)))
}
```

There are a number of guides that help with putting together Cache-Control headers:

* [Mozilla's Guide to HTTP caching](https://developer.mozilla.org/en-US/docs/Web/HTTP/Caching)
* [Mark Nottingham's Guide to Caching](https://www.mnot.net/cache_docs/)
* [HTTP Caching](https://developers.google.com/web/fundamentals/performance/optimizing-content-efficiency/http-caching)
* [REST Easy: HTTP Cache](http://odino.org/rest-better-http-cache/)

## Releasing a new version

See https://github.com/playframework/.github/blob/main/RELEASING.md

## License

Play WS is licensed under the Apache license, version 2. See the LICENSE file for more information.
