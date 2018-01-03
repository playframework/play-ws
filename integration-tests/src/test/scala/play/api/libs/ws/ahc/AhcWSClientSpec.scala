/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.specs2.concurrent.{ ExecutionEnv, FutureAwait }
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.api.libs.ws.{ BodyReadable, DefaultBodyReadables }

import scala.concurrent._

class AhcWSClientSpec(implicit val executionEnv: ExecutionEnv) extends Specification
  with AkkaServerProvider
  with FutureMatchers
  with FutureAwait
  with DefaultBodyReadables {

  def withClient(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Result): Result = {
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }

  override val routes = {
    import akka.http.scaladsl.server.Directives._
    get {
      complete("<h1>Say hello to akka-http</h1>")
    } ~
      post {
        entity(as[String]) { echo =>
          complete(echo)
        }
      }
  }

  "url" should {
    "throw an exception on invalid url" in {
      withClient() { client =>
        { client.url("localhost") } must throwAn[IllegalArgumentException]
      }
    }

    "not throw exception on valid url" in {
      withClient() { client =>
        { client.url(s"http://localhost:$testServerPort") } must not(throwAn[IllegalArgumentException])
      }
    }
  }

  "WSClient" should {

    "request a url as an in memory string" in {
      withClient() { client =>
        val result = Await.result(client.url(s"http://localhost:$testServerPort/index").get().map(res => res.body[String]), defaultTimeout)
        result must beEqualTo("<h1>Say hello to akka-http</h1>")
      }
    }

    "request a url as a Foo" in {
      case class Foo(body: String)

      implicit val fooBodyReadable = BodyReadable[Foo] { response =>
        import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }
        val ahcResponse = response.asInstanceOf[StandaloneAhcWSResponse].underlying[AHCResponse]
        Foo(ahcResponse.getResponseBody)
      }

      withClient() { client =>
        val result = Await.result(client.url(s"http://localhost:$testServerPort/index").get().map(res => res.body[Foo]), defaultTimeout)
        result must beEqualTo(Foo("<h1>Say hello to akka-http</h1>"))
      }
    }

    "request a url as a stream" in {
      withClient() { client =>
        val resultSource = Await.result(client.url(s"http://localhost:$testServerPort/index").stream().map(_.bodyAsSource), defaultTimeout)
        val bytes: ByteString = Await.result(resultSource.runWith(Sink.head), defaultTimeout)
        bytes.utf8String must beEqualTo("<h1>Say hello to akka-http</h1>")
      }
    }

  }
}
