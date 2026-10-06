/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.oauth.RequestToken
import play.api.libs.ws.DefaultBodyReadables
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSClientConfig
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.api.routing.sird._
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.handler.RedirectRefusedException

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.NonFatal

class Ahc3CompatibilitySpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with DefaultBodyReadables
    with DefaultBodyWritables {

  sequential

  private val streamedTail          = Promise[ByteString]()
  private val cancellationObserved  = Promise[Done]()
  private val crossOriginTargetHits = new AtomicInteger()
  private val downgradeTargetHits   = new AtomicInteger()
  private val largeChunk            = ByteString(new Array[Byte](16 * 1024))
  private val largeChunkCount       = 512L
  private val digestUsername        = "digest-user"
  private val digestPassword        = "digest-password"
  private val digestRealm           = "play-ws-rfc7616"
  private val digestNonce           = "0123456789abcdef"
  private val DigestParameter       = """([A-Za-z][A-Za-z0-9_-]*)=(?:\"([^\"]*)\"|([^,\s]+))""".r

  private def looseTlsConfig(http2Enabled: Boolean): AhcWSClientConfig = {
    val config = ConfigFactory
      .parseString("play.ws.ssl.loose.acceptAnyCertificate = true")
      .withFallback(ConfigFactory.defaultReference())
    AhcWSClientConfigFactory.forConfig(config).copy(http2Enabled = http2Enabled)
  }

  private def trustedTlsConfig(server: AlpnProtocolTestServer, http2Enabled: Boolean): AhcWSClientConfig = {
    val storePath = server.trustStorePath()
    val config    = ConfigFactory
      .parseString(
        s"""play.ws.ssl.trustManager.stores = [{ type = "PKCS12", path = "$storePath", password = "changeit" }]"""
      )
      .withFallback(ConfigFactory.defaultReference())
    AhcWSClientConfigFactory.forConfig(config).copy(http2Enabled = http2Enabled)
  }

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = {
    case GET(p"/compatibility/gated-stream") =>
      components.defaultActionBuilder {
        val body = Source.single(ByteString("first")) ++ Source.future(streamedTail.future)
        Results.Ok.chunked(body)
      }

    case GET(p"/compatibility/cancellable-stream") =>
      components.defaultActionBuilder {
        val body = (Source.single(ByteString("first")) ++ Source.maybe[ByteString])
          .watchTermination { (_, completion) =>
            completion.onComplete(_ => cancellationObserved.trySuccess(Done))(system.dispatcher)
            NotUsed
          }
        Results.Ok.chunked(body)
      }

    case GET(p"/compatibility/large-stream") =>
      components.defaultActionBuilder {
        Results.Ok.chunked(Source.repeat(largeChunk).take(largeChunkCount))
      }

    case POST(p"/compatibility/echo") =>
      components.defaultActionBuilder { request =>
        val bytes = request.body.asText
          .map(ByteString(_))
          .orElse(request.body.asRaw.flatMap(_.asBytes()))
          .getOrElse(ByteString.empty)
        Results.Ok(bytes)
      }

    case POST(p"/compatibility/replay-stream") =>
      components.defaultActionBuilder {
        Results.Redirect("/compatibility/echo", 307)
      }

    case POST(p"/compatibility/content-type") =>
      components.defaultActionBuilder { request =>
        Results.Ok(request.headers.get("Content-Type").getOrElse(""))
      }

    case POST(p"/compatibility/cross-origin-redirect") =>
      components.defaultActionBuilder {
        Results.Redirect(s"http://127.0.0.1:$testServerPort/compatibility/cross-origin-target", 307)
      }

    case POST(p"/compatibility/same-origin-redirect") =>
      components.defaultActionBuilder {
        Results.Redirect("/compatibility/echo", 307)
      }

    case POST(p"/compatibility/cross-origin-target") =>
      components.defaultActionBuilder { request =>
        crossOriginTargetHits.incrementAndGet()
        Results.Ok(request.body.asText.getOrElse(""))
      }

    case GET(p"/compatibility/downgrade-target") =>
      components.defaultActionBuilder {
        downgradeTargetHits.incrementAndGet()
        Results.Ok("downgraded")
      }

    case GET(p"/compatibility/oauth") =>
      components.defaultActionBuilder { request =>
        Results.Ok(request.headers.get("Authorization").getOrElse(""))
      }

    case GET(p"/compatibility/digest") =>
      components.defaultActionBuilder { request =>
        request.headers.get("Authorization") match {
          case Some(authorization) if validSha256DigestAuthorization(authorization) =>
            Results.Ok("authenticated")
          case _ =>
            Results.Unauthorized.withHeaders(
              "WWW-Authenticate" ->
                s"""Digest realm="$digestRealm", nonce="$digestNonce", algorithm=SHA-256, qop="auth""""
            )
        }
      }

    case GET(p"/compatibility/delayed") =>
      components.defaultActionBuilder.async {
        org.apache.pekko.pattern.after(300.millis, system.scheduler)(Future.successful(Results.Ok("delayed")))
      }
  }

  "The AHC 3 upgrade" should {

    "use HTTP/1.1 over HTTPS by default even when the server offers h2" in {
      val server = new AlpnProtocolTestServer()
      try {
        withClient(trustedTlsConfig(server, http2Enabled = false)) { client =>
          val response = Await.result(client.url(server.url()).get(), defaultTimeout)
          (response.body[String] must beEqualTo("http/1.1")).and(
            server.negotiatedProtocol() must beEqualTo("http/1.1")
          )
        }
      } finally {
        server.close()
      }
    }

    "not advertise h2 over HTTPS merely because AHC HTTP/2 is enabled" in {
      val server = new AlpnProtocolTestServer()
      try {
        withClient(trustedTlsConfig(server, http2Enabled = true)) { client =>
          val response = Await.result(client.url(server.url()).get(), defaultTimeout)
          (response.body[String] must beEqualTo("http/1.1")).and(
            server.negotiatedProtocol() must beEqualTo("http/1.1")
          )
        }
      } finally {
        server.close()
      }
    }

    "use HTTP/1.1 on the loose TLS path even when the server offers h2" in {
      val server = new AlpnProtocolTestServer()
      try {
        withClient(looseTlsConfig(http2Enabled = false)) { client =>
          val response = Await.result(client.url(server.url()).get(), defaultTimeout)
          (response.body[String] must beEqualTo("http/1.1")).and(
            server.negotiatedProtocol() must beEqualTo("http/1.1")
          )
        }
      } finally {
        server.close()
      }
    }

    "not advertise h2 on the loose TLS path merely because AHC HTTP/2 is enabled" in {
      val server = new AlpnProtocolTestServer()
      try {
        withClient(looseTlsConfig(http2Enabled = true)) { client =>
          val response = Await.result(client.url(server.url()).get(), defaultTimeout)
          (response.body[String] must beEqualTo("http/1.1")).and(
            server.negotiatedProtocol() must beEqualTo("http/1.1")
          )
        }
      } finally {
        server.close()
      }
    }

    "retain cross-origin body replay when the refusal is unset" in {
      val before = crossOriginTargetHits.get()
      val base   = AhcWSClientConfigFactory.forConfig()
      val config = base.copy(wsClientConfig = base.wsClientConfig.copy(followRedirects = true))

      withClient(config) { client =>
        val response = Await.result(
          client.url(s"http://localhost:$testServerPort/compatibility/cross-origin-redirect").post("request-body"),
          defaultTimeout
        )
        (response.body[String] must beEqualTo("request-body")).and(
          crossOriginTargetHits.get() must beEqualTo(before + 1)
        )
      }
    }

    "refuse cross-origin request-body replay before contacting the target when enabled" in {
      val before = crossOriginTargetHits.get()
      val base   = AhcWSClientConfigFactory.forConfig()
      val config = base.copy(
        wsClientConfig = base.wsClientConfig.copy(followRedirects = true),
        refuseCrossOriginBodyOnRedirect = Some(true)
      )

      withClient(config) { client =>
        val failure = captureFailure {
          Await.result(
            client.url(s"http://localhost:$testServerPort/compatibility/cross-origin-redirect").post("request-body"),
            defaultTimeout
          )
        }
        (redirectRefusal(failure).map(_.getReason) must beSome(RedirectRefusedException.Reason.CROSS_ORIGIN_BODY))
          .and(crossOriginTargetHits.get() must beEqualTo(before))
      }
    }

    "allow same-origin request-body replay when both refusals are enabled" in {
      val base   = AhcWSClientConfigFactory.forConfig()
      val config = base.copy(
        wsClientConfig = base.wsClientConfig.copy(followRedirects = true),
        refuseCrossOriginBodyOnRedirect = Some(true),
        refuseSchemeDowngradeOnRedirect = Some(true)
      )

      withClient(config) { client =>
        val response = Await.result(
          client.url(s"http://localhost:$testServerPort/compatibility/same-origin-redirect").post("request-body"),
          defaultTimeout
        )
        response.body[String] must beEqualTo("request-body")
      }
    }

    "surface cross-origin body refusals through the Java client API" in {
      val before = crossOriginTargetHits.get()
      val base   = AhcWSClientConfigFactory.forConfig()
      val config = base.copy(
        wsClientConfig = base.wsClientConfig.copy(followRedirects = true),
        refuseCrossOriginBodyOnRedirect = Some(true)
      )
      val ahcClient  = new DefaultAsyncHttpClient(new AhcConfigBuilder(config).build())
      val javaClient = new play.libs.ws.ahc.StandaloneAhcWSClient(ahcClient, materializer)
      try {
        val failure = captureFailure {
          javaClient
            .url(s"http://localhost:$testServerPort/compatibility/cross-origin-redirect")
            .post(new play.libs.ws.InMemoryBodyWritable(ByteString("request-body"), "text/plain"))
            .toCompletableFuture
            .get(defaultTimeout.toMillis, TimeUnit.MILLISECONDS)
        }
        (redirectRefusal(failure).map(_.getReason) must beSome(RedirectRefusedException.Reason.CROSS_ORIGIN_BODY))
          .and(crossOriginTargetHits.get() must beEqualTo(before))
      } finally {
        javaClient.close()
      }
    }

    "retain HTTPS-to-HTTP redirects when the refusal is unset" in {
      val before = downgradeTargetHits.get()
      val server = new AlpnProtocolTestServer(s"http://localhost:$testServerPort/compatibility/downgrade-target")
      try {
        val base   = trustedTlsConfig(server, http2Enabled = false)
        val config = base.copy(wsClientConfig = base.wsClientConfig.copy(followRedirects = true))
        withClient(config) { client =>
          val response = Await.result(client.url(server.url()).get(), defaultTimeout)
          (response.body[String] must beEqualTo("downgraded")).and(
            downgradeTargetHits.get() must beEqualTo(before + 1)
          )
        }
      } finally {
        server.close()
      }
    }

    "refuse HTTPS-to-HTTP redirects before contacting the target when enabled" in {
      val before = downgradeTargetHits.get()
      val server = new AlpnProtocolTestServer(s"http://localhost:$testServerPort/compatibility/downgrade-target")
      try {
        val base   = trustedTlsConfig(server, http2Enabled = false)
        val config = base.copy(
          wsClientConfig = base.wsClientConfig.copy(followRedirects = true),
          refuseSchemeDowngradeOnRedirect = Some(true)
        )
        withClient(config) { client =>
          val failure = captureFailure {
            Await.result(client.url(server.url()).get(), defaultTimeout)
          }
          (redirectRefusal(failure).map(_.getReason) must beSome(RedirectRefusedException.Reason.SCHEME_DOWNGRADE))
            .and(downgradeTargetHits.get() must beEqualTo(before))
        }
      } finally {
        server.close()
      }
    }

    "keep response streaming incremental" in withClient() { client =>
      val response = Await.result(
        client.url(s"http://localhost:$testServerPort/compatibility/gated-stream").stream(),
        defaultTimeout
      )
      val queue = response.bodyAsSource.runWith(Sink.queue())

      Await.result(queue.pull(), defaultTimeout) must beSome(ByteString("first"))
      val tail = queue.pull()
      tail.value must beNone

      streamedTail.success(ByteString("second"))
      Await.result(tail, defaultTimeout) must beSome(ByteString("second"))
      Await.result(queue.pull(), defaultTimeout) must beNone
    }

    "propagate streamed response cancellation" in withClient() { client =>
      val response = Await.result(
        client.url(s"http://localhost:$testServerPort/compatibility/cancellable-stream").stream(),
        defaultTimeout
      )
      val queue = response.bodyAsSource.runWith(Sink.queue())

      Await.result(queue.pull(), defaultTimeout) must beSome(ByteString("first"))
      queue.cancel()
      Await.result(cancellationObserved.future, defaultTimeout) must beEqualTo(Done)
    }

    "stop HTTP/1.1 transport reads while there is no downstream demand" in {
      val server = new BackpressureTestServer()
      try {
        withClient() { client =>
          val response  = Await.result(client.url(server.url()).stream(), defaultTimeout)
          val stalledAt = server.awaitWriteStall(5000, 300)

          response.bodyAsSource.runWith(Sink.cancelled)

          // Allow for platform socket-buffer differences, but require a stall far below the 64 MiB response.
          (stalledAt must beLessThan(1024L * 1024L)).and(
            server.bytesWritten() must beLessThan(server.totalBytes())
          )
        }
      } finally {
        server.close()
      }
    }

    "stream a multi-megabyte response" in withClient() { client =>
      val response = Await.result(
        client.url(s"http://localhost:$testServerPort/compatibility/large-stream").stream(),
        defaultTimeout
      )
      val received = Await.result(
        response.bodyAsSource.runFold(0L)((total, chunk) => total + chunk.length),
        30.seconds
      )

      received must beEqualTo(largeChunk.length.toLong * largeChunkCount)
    }

    "keep a demanded HTTP/2 stream moving while a sibling is suspended" in {
      val server = new Http2StreamingTestServer()
      // A stream window above the connection window (65,535 bytes) lets the suspended stream alone use up the
      // connection's credit. AHC returns connection credit while a response is suspended; its own tests verify that.
      val ahcConfig = new AhcConfigBuilder(AhcWSClientConfigFactory.forConfig())
        .modifyUnderlying { builder =>
          builder
            .setHttp2Enabled(true)
            .setHttp2CleartextEnabled(true)
            .setHttp2InitialWindowSize(128 * 1024)
            .setMaxConnectionsPerHost(1)
        }
        .build()
      val client = new StandaloneAhcWSClient(new DefaultAsyncHttpClient(ahcConfig))

      try {
        val suspended = Await.result(client.url(server.url("/suspended")).stream(), defaultTimeout)
        server.awaitSuspendedResponseQueued(5, TimeUnit.SECONDS) must beTrue
        val sibling  = Await.result(client.url(server.url("/sibling")).stream(), defaultTimeout)
        val received = Await.result(
          sibling.bodyAsSource.runFold(0L)((total, chunk) => total + chunk.length),
          10.seconds
        )
        // Checked before cancelling, which may release the suspended stream's credit.
        val heldBack = server.suspendedResponseStillPending()
        suspended.bodyAsSource.runWith(Sink.cancelled)

        (received must beEqualTo(server.siblingResponseBytes())).toResult
          .and((server.connectionCount().toLong must beEqualTo(1L)).toResult)
          .and((heldBack must beTrue.setMessage("the suspended response was not held back")).toResult)
      } finally {
        client.close()
        server.close()
      }
    }

    "keep Source, File, and InputStream request bodies" in withClient() { client =>
      def post[T: play.api.libs.ws.BodyWritable](body: T): String = {
        Await.result(
          client
            .url(s"http://localhost:$testServerPort/compatibility/echo")
            .post(body)
            .map(_.body[String]),
          defaultTimeout
        )
      }

      val sourceResult = post(Source(List(ByteString("source-"), ByteString("body"))))

      val file       = Files.createTempFile("play-ws-ahc3-compatibility", ".txt")
      val fileResult = try {
        Files.write(file, "file-body".getBytes(StandardCharsets.UTF_8))
        post(file.toFile)
      } finally {
        Files.deleteIfExists(file)
      }

      val supplier = new Supplier[InputStream] {
        override def get(): InputStream = new ByteArrayInputStream("input-stream-body".getBytes(StandardCharsets.UTF_8))
      }

      (sourceResult must beEqualTo("source-body"))
        .and(fileResult must beEqualTo("file-body"))
        .and(post(supplier) must beEqualTo("input-stream-body"))
    }

    "fail a streamed request-body redirect with a clear replay error" in {
      val baseConfig = AhcWSClientConfigFactory.forConfig()
      val config     = baseConfig.copy(
        wsClientConfig = baseConfig.wsClientConfig.copy(followRedirects = true)
      )

      withClient(config) { client =>
        val failure = captureFailure {
          Await.result(
            client
              .url(s"http://localhost:$testServerPort/compatibility/replay-stream")
              .post(Source.single(ByteString("streamed-body"))),
            defaultTimeout
          )
        }

        causeMessages(failure) must contain("A streamed request body cannot be replayed")
      }
    }

    "fail a streamed request-body retry with a clear replay error" in {
      val server         = new ServerSocket(0)
      val serverExecutor = Executors.newSingleThreadExecutor()
      val firstAttempt   = serverExecutor.submit(new Runnable {
        override def run(): Unit = {
          val socket = server.accept()
          try {
            socket.setSoTimeout(TimeUnit.SECONDS.toMillis(5).toInt)
            consumeHttpRequest(socket.getInputStream)
          } finally {
            socket.close()
          }
        }
      })

      try {
        val config = AhcWSClientConfigFactory.forConfig().copy(maxRequestRetry = 1)
        withClient(config) { client =>
          val failure = captureFailure {
            Await.result(
              client
                .url(s"http://127.0.0.1:${server.getLocalPort}/retry-stream")
                .post(Source.single(ByteString("streamed-body"))),
              defaultTimeout
            )
          }

          firstAttempt.get(5, TimeUnit.SECONDS)
          causeMessages(failure) must contain("A streamed request body cannot be replayed")
        }
      } finally {
        server.close()
        serverExecutor.shutdownNow()
      }
    }

    "keep OAuth 1 request signing" in withClient() { client =>
      val calculator    = OAuthCalculator(ConsumerKey("consumer", "secret"), RequestToken("token", "token-secret"))
      val authorization = Await.result(
        client
          .url(s"http://localhost:$testServerPort/compatibility/oauth")
          .sign(calculator)
          .get()
          .map(_.body[String]),
        defaultTimeout
      )

      authorization must startWith("OAuth ")
    }

    "support RFC 7616 SHA-256 Digest authentication" in withClient() { client =>
      val response = Await.result(
        client
          .url(s"http://localhost:$testServerPort/compatibility/digest")
          .withAuth(digestUsername, digestPassword, WSAuthScheme.DIGEST)
          .get(),
        defaultTimeout
      )

      (response.status, response.body[String]) must beEqualTo((200, "authenticated"))
    }

    "keep a per-request infinite timeout" in {
      val config = AhcWSClientConfigFactory
        .forConfig()
        .copy(wsClientConfig = WSClientConfig(requestTimeout = 100.millis))

      withClient(config) { client =>
        val response = Await.result(
          client
            .url(s"http://localhost:$testServerPort/compatibility/delayed")
            .withRequestTimeout(Duration.Inf)
            .get(),
          defaultTimeout
        )
        response.body[String] must beEqualTo("delayed")
      }
    }

    "keep implicit and explicit content types" in withClient() { client =>
      def contentType(request: StandaloneWSRequest, body: String): String = {
        Await.result(request.post(body).map(_.body[String]), defaultTimeout)
      }

      val url                 = s"http://localhost:$testServerPort/compatibility/content-type"
      val implicitContentType = contentType(client.url(url), "body")
      val explicitContentType =
        contentType(client.url(url).withHttpHeaders("Content-Type" -> "application/json"), """{"body":"value"}""")

      (implicitContentType must beEqualTo("text/plain; charset=UTF-8")).and(
        explicitContentType must beEqualTo("application/json")
      )
    }
  }

  private def captureFailure(block: => Any): Throwable = {
    try {
      block
      throw new AssertionError("Expected the request to fail")
    } catch {
      case NonFatal(failure) => failure
    }
  }

  private def causeMessages(failure: Throwable): Seq[String] = {
    Iterator
      .iterate(Option(failure))(_.flatMap(current => Option(current.getCause)))
      .takeWhile(_.nonEmpty)
      .flatten
      .flatMap(current => Option(current.getMessage))
      .toSeq
  }

  private def redirectRefusal(failure: Throwable): Option[RedirectRefusedException] = {
    Iterator
      .iterate(Option(failure))(_.flatMap(current => Option(current.getCause)))
      .takeWhile(_.nonEmpty)
      .flatten
      .collectFirst { case refusal: RedirectRefusedException => refusal }
  }

  private def validSha256DigestAuthorization(authorization: String): Boolean = {
    if (!authorization.startsWith("Digest ")) {
      false
    } else {
      val parameters = DigestParameter
        .findAllMatchIn(authorization.substring("Digest ".length))
        .map { matched =>
          val value = Option(matched.group(2)).getOrElse(matched.group(3))
          matched.group(1).toLowerCase(Locale.ROOT) -> value
        }
        .toMap

      val expectedUri = "/compatibility/digest"
      (for {
        username    <- parameters.get("username")
        realm       <- parameters.get("realm")
        nonce       <- parameters.get("nonce")
        uri         <- parameters.get("uri")
        algorithm   <- parameters.get("algorithm")
        qop         <- parameters.get("qop")
        nonceCount  <- parameters.get("nc")
        clientNonce <- parameters.get("cnonce")
        response    <- parameters.get("response")
      } yield {
        val ha1              = sha256(s"$digestUsername:$digestRealm:$digestPassword")
        val ha2              = sha256(s"GET:$expectedUri")
        val expectedResponse = sha256(s"$ha1:$digestNonce:$nonceCount:$clientNonce:auth:$ha2")

        username == digestUsername &&
        realm == digestRealm &&
        nonce == digestNonce &&
        uri == expectedUri &&
        algorithm.equalsIgnoreCase("SHA-256") &&
        qop.equalsIgnoreCase("auth") &&
        nonceCount.matches("(?i)[0-9a-f]{8}") &&
        clientNonce.nonEmpty &&
        response.equalsIgnoreCase(expectedResponse)
      }).getOrElse(false)
    }
  }

  private def sha256(value: String): String = {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.ISO_8859_1))
    val hex   = new StringBuilder(bytes.length * 2)
    bytes.foreach { byte =>
      val unsigned = byte & 0xff
      hex.append(Character.forDigit(unsigned >>> 4, 16))
      hex.append(Character.forDigit(unsigned & 0x0f, 16))
    }
    hex.result()
  }

  private def consumeHttpRequest(input: InputStream): Unit = {
    val headers = Iterator.continually(readAsciiLine(input)).takeWhile(_.nonEmpty).toSeq
    headers.collectFirst {
      case header if header.toLowerCase.startsWith("content-length:") =>
        header.substring(header.indexOf(':') + 1).trim.toLong
    } match {
      case Some(contentLength)                                                      => readFully(input, contentLength)
      case None if headers.exists(_.equalsIgnoreCase("transfer-encoding: chunked")) =>
        var complete = false
        while (!complete) {
          val size = Integer.parseInt(readAsciiLine(input).takeWhile(_ != ';'), 16)
          if (size == 0) {
            Iterator.continually(readAsciiLine(input)).takeWhile(_.nonEmpty).foreach(_ => ())
            complete = true
          } else {
            readFully(input, size)
            readAsciiLine(input)
          }
        }
      case None => ()
    }
  }

  private def readFully(input: InputStream, length: Long): Unit = {
    var remaining = length
    val buffer    = new Array[Byte](8192)
    while (remaining > 0) {
      val read = input.read(buffer, 0, Math.min(buffer.length.toLong, remaining).toInt)
      if (read < 0) {
        throw new IllegalStateException("Request ended before its declared body length")
      }
      remaining -= read
    }
  }

  private def readAsciiLine(input: InputStream): String = {
    val line     = new ByteArrayOutputStream
    var previous = -1
    var current  = input.read()
    while (current >= 0 && !(previous == '\r' && current == '\n')) {
      if (previous >= 0) {
        line.write(previous)
      }
      previous = current
      current = input.read()
    }
    if (current < 0) {
      throw new IllegalStateException("Request ended before the HTTP line was complete")
    }
    new String(line.toByteArray, StandardCharsets.US_ASCII)
  }
}
