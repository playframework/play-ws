/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.specs2.concurrent.{ ExecutionEnv, FutureAwait }
import org.specs2.execute.{ Result }
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.api.libs.ws.akkahttp.StandaloneAkkaHttpWSClient
import play.api.libs.ws.{ BodyReadable, DefaultBodyReadables, StandaloneWSClient }

import scala.concurrent._

class AhcWSClientSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val config = AhcWSClientConfigFactory.forConfig()
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}

class AkkaHttpWSClientSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val client = StandaloneAkkaHttpWSClient()
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with FutureAwait
    with DefaultBodyReadables
    with AkkaHttpPending {

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

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
      }.akkaHttpPending("Akka Http accepts hostname as URI")
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
        Foo(response.body)
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
