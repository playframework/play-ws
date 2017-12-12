/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.stream.scaladsl.Sink
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.xml.Elem

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with DefaultBodyReadables {

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
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .map(_.body[String])
          .map(_ must beEqualTo("<h1>Say hello to akka-http</h1>"))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a Foo" in {
      case class Foo(body: String)

      implicit val fooBodyReadable = BodyReadable[Foo] { response =>
        Foo(response.body)
      }

      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .map(_.body[Foo])
          .map(_ must beEqualTo(Foo("<h1>Say hello to akka-http</h1>")))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a stream" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .stream()
          .map(_.bodyAsSource)
          .flatMap(_.runWith(Sink.head))
          .map(_.utf8String must beEqualTo("<h1>Say hello to akka-http</h1>"))
          .awaitFor(defaultTimeout)
      }
    }

    "post a request" in {
      import DefaultBodyWritables._
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .post("hello world")
          .map(_.body must be_==("hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "round trip XML" in {
      val document = XML.parser.loadString(
        """<?xml version="1.0" encoding='UTF-8'?>
          |<note>
          |  <from>hello</from>
          |  <to>world</to>
          |</note>""".stripMargin)

      import XMLBodyWritables._
      import XMLBodyReadables._
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .post(document)
          .map(_.body[Elem])
          .map(_ must be_==(document))
          .awaitFor(defaultTimeout)
      }
    }

  }
}
