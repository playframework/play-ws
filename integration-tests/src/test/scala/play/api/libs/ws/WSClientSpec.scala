/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.NotUsed
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.model.headers.{ Host, HttpCookie }
import akka.http.scaladsl.server.directives.Credentials
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.concurrent.{ Future, TimeoutException }
import scala.concurrent.duration._
import scala.xml.Elem

object WSClientSpec {
  private def authenticator(credentials: Credentials): Option[String] =
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("pass") => Some(id)
      case _ => None
    }

  val routes = {
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.marshalling.Marshaller._
    path("xml") {
      entity(as[String]) { echo =>
        complete(echo)
      }
    } ~
      path("auth" / "basic") {
        authenticateBasic(realm = "secure site", authenticator) { id =>
          complete(s"Authenticated $id")
        }
      } ~
      path("virtualhost") {
        extractRequest { r =>
          complete(r.header[Host].get.host.address)
        }
      } ~
      path("timeout") {
        extractActorSystem { sys =>
          import sys.dispatcher
          complete(akka.pattern.after(2.seconds, sys.scheduler)(Future.successful("timeout")))
        }
      } ~
      path("204") {
        complete(StatusCodes.NoContent)
      } ~
      path("stream") {
        val source = Source("streamed".toIndexedSeq).map(c => ByteString(c.toString))
        val httpEntity = HttpEntity(ContentTypes.`application/octet-stream`, source)
        complete(httpEntity)
      } ~
      path("cookies") {
        extractRequest { r =>
          val cookies = r.cookies.map(c => HttpCookie(c.name, c.value)) :+ HttpCookie("cookie3", "cookie3")
          setCookie(cookies.head, cookies.tail: _*) {
            complete("OK")
          }
        }
      } ~
      get {
        entity(as[String]) { echo =>
          complete(s"GET $echo")
        }
      } ~
      post {
        entity(as[String]) { echo =>
          complete(s"POST $echo")
        }
      } ~
      patch {
        entity(as[String]) { echo =>
          complete(s"PATCH $echo")
        }
      } ~
      put {
        entity(as[String]) { echo =>
          complete(s"PUT $echo")
        }
      } ~
      delete {
        entity(as[String]) { echo =>
          complete("DELETE")
        }
      } ~
      head {
        complete(StatusCodes.OK)
      } ~
      options {
        entity(as[String]) { echo =>
          complete("OPTIONS")
        }
      }
  }
}

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with DefaultBodyReadables {

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

  override val routes = WSClientSpec.routes

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

    "return underlying implementations" in {
      withClient() { client =>
        client.underlying.getClass.getName must beOneOf(
          "play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient",
          "akka.http.scaladsl.HttpExt"
        )

        client
          .url(s"http://localhost:$testServerPort/index")
          .get()
          .map {
            _.underlying.getClass.getName must beOneOf(
              "play.shaded.ahc.org.asynchttpclient.netty.NettyResponse",
              "akka.http.scaladsl.model.HttpResponse"
            )
          }
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as an in memory string" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .map(_.body[String])
          .map(_ must beEqualTo("GET "))
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
          .map(_ must beEqualTo(Foo("GET ")))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a stream" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .stream()
          .map(_.bodyAsSource)
          .flatMap(_.runWith(Sink.head))
          .map(_.utf8String must beEqualTo("GET "))
          .awaitFor(defaultTimeout)
      }
    }

    "send post request" in {
      import DefaultBodyWritables._
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .post("hello world")
          .map(_.body must be_==("POST hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send patch request" in {
      import DefaultBodyWritables._
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .patch("hello world")
          .map(_.body must be_==("PATCH hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send put request" in {
      import DefaultBodyWritables._
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .put("hello world")
          .map(_.body must be_==("PUT hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send delete request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .delete()
          .map(_.body must be_==("DELETE"))
          .awaitFor(defaultTimeout)
      }
    }

    "send head request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .head()
          .map(_.status must be_==(200))
          .awaitFor(defaultTimeout)
      }
    }

    "send options request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .options()
          .map(_.body must be_==("OPTIONS"))
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
        _.url(s"http://localhost:$testServerPort/xml")
          .post(document)
          .map(_.body[Elem])
          .map(_ must be_==(document))
          .awaitFor(defaultTimeout)
      }
    }

    "authenticate basic" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/auth/basic")
          .withAuth("user", "pass", WSAuthScheme.BASIC)
          .get()
          .map(_.body)
          .map(_ must be_==("Authenticated user"))
          .awaitFor(defaultTimeout)
      }
    }

    "set host header" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/virtualhost")
          .withVirtualHost("virtualhost")
          .get()
          .map(_.body must be_==("virtualhost"))
          .awaitFor(defaultTimeout)
      }
    }

    "complete after timeout" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/timeout")
          .withRequestTimeout(100.millis)
          .get()
          .map(_ => failure)
          .recover {
            case ex =>
              ex must beAnInstanceOf[TimeoutException]
              ex.getMessage must startWith("Request timeout")
              success
          }
          .awaitFor(defaultTimeout)
      }
    }

    "provide response status text" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/204")
          .get()
          .map(_.statusText must be_==("No Content"))
          .awaitFor(defaultTimeout)
      }
    }

    "allow access body more than one time" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/stream")
          .get()
          .map { resp =>
            resp.body[String] must beEqualTo("streamed")
            resp.body[String] must beEqualTo("streamed")
          }
          .awaitFor(defaultTimeout)
      }
    }

    "send and receive cookies" in {
      withClient() { client =>
        val cookie1 = DefaultWSCookie("cookie1", "cookie1")
        val cookie2 = DefaultWSCookie("cookie2", "cookie2")
        val cookie3 = DefaultWSCookie("cookie3", "cookie3")

        val request = client
          .url(s"http://localhost:$testServerPort/cookies")
          .addCookies(cookie1)

        request.cookies must containTheSameElementsAs(Seq(cookie1))

        request.withCookies(cookie2).cookies must containTheSameElementsAs(Seq(cookie2))

        request
          .addCookies(cookie2)
          .get()
          .map { resp =>
            resp.cookies must containTheSameElementsAs(Seq(cookie1, cookie2, cookie3))
            resp.cookie(cookie1.name) must be_==(Some(cookie1))
          }
          .awaitFor(defaultTimeout)
      }
    }

  }
}
