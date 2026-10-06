/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Supplier

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
import play.api.libs.ws.WSClientConfig
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.api.routing.sird._

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

class Ahc3CompatibilitySpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with DefaultBodyReadables
    with DefaultBodyWritables {

  sequential

  private val streamedTail         = Promise[ByteString]()
  private val cancellationObserved = Promise[Done]()

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

    case POST(p"/compatibility/echo") =>
      components.defaultActionBuilder { request =>
        val bytes = request.body.asText
          .map(ByteString(_))
          .orElse(request.body.asRaw.flatMap(_.asBytes()))
          .getOrElse(ByteString.empty)
        Results.Ok(bytes)
      }

    case POST(p"/compatibility/content-type") =>
      components.defaultActionBuilder { request =>
        Results.Ok(request.headers.get("Content-Type").getOrElse(""))
      }

    case GET(p"/compatibility/oauth") =>
      components.defaultActionBuilder { request =>
        Results.Ok(request.headers.get("Authorization").getOrElse(""))
      }

    case GET(p"/compatibility/delayed") =>
      components.defaultActionBuilder.async {
        org.apache.pekko.pattern.after(300.millis, system.scheduler)(Future.successful(Results.Ok("delayed")))
      }
  }

  "The AHC 3 upgrade" should {

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
}
