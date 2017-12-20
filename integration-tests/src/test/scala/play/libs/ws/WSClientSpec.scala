/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws

import java.net.MalformedURLException
import java.time.Duration

import akka.stream.javadsl.{ Sink, Source }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._
import scala.concurrent.TimeoutException

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with DefaultBodyWritables
    with XMLBodyWritables with XMLBodyReadables {

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

  override val routes = play.api.libs.ws.WSClientSpec.routes

  "WSClient" should {
    "throw an exception on invalid url" in {
      withClient() { client =>
        // akka http parses no scheme properly
        { client.url("httt://localhost") } must (throwAn[RuntimeException] like {
          case ex: RuntimeException =>
            ex.getCause must beAnInstanceOf[MalformedURLException]
        })
      }
    }

    "not throw exception on valid url" in {
      withClient() { client =>
        { client.url(s"http://localhost:$testServerPort") } must not(throwAn[IllegalArgumentException])
      }
    }

    "set the basic request parameters" in {
      withClient() { client =>
        val request = client.url(s"http://localhost:$testServerPort")

        request.getUrl must be_==(s"http://localhost:$testServerPort")
        request.getContentType must beNull

        client
          .url(s"http://localhost:$testServerPort")
          .setContentType("text/plain")
          .getContentType must be_==("text/plain")

        client
          .url(s"http://localhost:$testServerPort")
          .setBody(body("text"))
          .getContentType must be_==("text/plain")

        client
          .url(s"http://localhost:$testServerPort")
          .setBody(body(Source.empty()))
          .getContentType must be_==("application/octet-stream")
      }
    }

    "correctly URL-encode the query string part" in {
      withClient() { client =>
        val request = client.url("http://example.com")
          .setQueryString(Map("&" -> Seq("=").asJava).asJava)

        request.getUrl must equalTo("http://example.com")
        request.getQueryParameters.asScala.mapValues(_.asScala) must be_==(Map("&" -> Seq("=")))
      }
    }

    "discard old query parameters when setting new ones" in {
      withClient() {
        _.url("http://example.com")
          .setQueryString(Map("bar" -> Seq("baz").asJava).asJava)
          .setQueryString(Map("bar" -> Seq("bah").asJava).asJava)
          .getQueryParameters.asScala.mapValues(_.asScala) must be_==(Map("bar" -> Seq("bah")))
      }
    }

    "add query string param" in {
      withClient() {
        _.url("http://example.com")
          .setQueryString("bar=baz")
          .addQueryParameter("bar", "bah")
          .getQueryParameters.asScala.mapValues(_.asScala) must be_==(Map("bar" -> Seq("baz", "bah")))
      }
    }

    "support adding several query string values for a parameter" in {
      // need to pass in mutable list,
      // as the AHC implementation stores the passed in list
      // and then adds elements to it from other API calls
      val list = new java.util.ArrayList[String]().asInstanceOf[java.util.List[String]]
      list.add("foo1")
      list.add("foo2")
      withClient() { client =>
        val request = client
          .url("http://example.com")
          .setQueryString(Map("play" -> list).asJava)
          .addQueryParameter("play", "foo3")

        request.getQueryParameters.get("play").asScala must containTheSameElementsAs(Seq("foo1", "foo2", "foo3"))
      }
    }

    "support adding headers" in {
      // need to pass in mutable list,
      // as the AHC implementation stores the passed in list
      // and then adds elements to it from other API calls
      val list = new java.util.ArrayList[String]().asInstanceOf[java.util.List[String]]
      list.add("value1")
      withClient() { client =>
        val request = client.url("http://playframework.com/")
          .setHeaders(Map("key" -> list).asJava)
          .addHeader("key", "value2")

        request.getHeaders.get("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
        request.getHeaderValues("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
        request.getHeader("key").asScala must beSome("value1")

        request.getHeaderValues("raktas").asScala must beEmpty
        request.getHeader("raktas").asScala must beNone

        request
          .setHeaders(Map("key" -> Seq("value1").asJava).asJava)
          .getHeaders.get("key").asScala must containTheSameElementsAs(Seq("value1"))
      }
    }

    "not make Content-Type header if there is Content-Type in headers already" in {
      withClient() {
        _.url("http://playframework.com/")
          .addHeader("Content-Type", "fake/contenttype; charset=utf-8")
          .setBody(body("I am a text/plain body"))
          .getHeader("Content-Type").asScala.map(_.toLowerCase) must beSome("fake/contenttype; charset=utf-8")
      }
    }

    // FIXME this is different from Scala API but this is how AHC implementation is
    "treat headers as case sensitive" in {
      withClient() {
        _.url("http://playframework.com/")
          .setHeaders(Map("key" -> Seq("value1").asJava, "KEY" -> Seq("value2").asJava).asJava)
          .getHeaderValues("key").asScala must be_==(Seq("value1"))
      }
    }

    "return underlying implementations" in {
      withClient() { client =>
        client.getUnderlying.getClass.getName must beOneOf(
          "play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient",
          "akka.http.javadsl.Http"
        )

        client
          .url(s"http://localhost:$testServerPort/index")
          .get()
          .toScala
          .map {
            _.getUnderlying.getClass.getName must beOneOf(
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
          .toScala
          .map(_.getBody)
          .map(_ must beEqualTo("GET "))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a Foo" in {
      case class Foo(body: String)

      val fooBodyReadable = new BodyReadable[Foo] {
        override def apply(t: StandaloneWSResponse): Foo = Foo(t.getBody)
      }

      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .toScala
          .map(_.getBody(fooBodyReadable))
          .map(_ must beEqualTo(Foo("GET ")))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a stream" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .stream()
          .toScala
          .map(_.getBodyAsSource)
          .flatMap(_.runWith(Sink.head(), materializer).toScala)
          .map(_.utf8String must beEqualTo("GET "))
          .awaitFor(defaultTimeout)
      }
    }

    "send post request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .post(body("hello world"))
          .toScala
          .map(_.getBody must be_==("POST hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send patch request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .patch(body("hello world"))
          .toScala
          .map(_.getBody must be_==("PATCH hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send put request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .put(body("hello world"))
          .toScala
          .map(_.getBody must be_==("PUT hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send delete request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .delete()
          .toScala
          .map(_.getBody must be_==("DELETE"))
          .awaitFor(defaultTimeout)
      }
    }

    "send head request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .head()
          .toScala
          .map(_.getStatus must be_==(200))
          .awaitFor(defaultTimeout)
      }
    }

    "send options request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .options()
          .toScala
          .map(_.getBody must be_==("OPTIONS"))
          .awaitFor(defaultTimeout)
      }
    }

    "round trip XML" in {
      val document = XML.fromString(
        """<?xml version="1.0" encoding='UTF-8'?>
          |<note>
          |  <from>hello</from>
          |  <to>world</to>
          |</note>""".stripMargin)
      document.normalizeDocument()

      withClient() {
        _.url(s"http://localhost:$testServerPort/xml")
          .post(body(document))
          .toScala
          .map { resp =>
            resp.getContentType must be_==("text/plain; charset=UTF-8")
            resp
          }
          .map(_.getBody(xml()))
          .map(_.isEqualNode(document) must be_==(true))
          .awaitFor(defaultTimeout)
      }
    }

    "authenticate basic" in {
      withClient() { client =>
        val requestWithoutAuth = client.url(s"http://localhost:$testServerPort/auth/basic")
        requestWithoutAuth.getUsername must beNull
        requestWithoutAuth.getPassword must beNull
        requestWithoutAuth.getScheme must beNull

        val requestWithAuth = requestWithoutAuth
          .setAuth("user", "pass", WSAuthScheme.BASIC)

        requestWithAuth.getUsername must be_==("user")
        requestWithAuth.getPassword must be_==("pass")
        requestWithAuth.getScheme must be_==(WSAuthScheme.BASIC)

        requestWithAuth
          .get()
          .toScala
          .map(_.getBody)
          .map(_ must be_==("Authenticated user"))
          .awaitFor(defaultTimeout)
      }
    }

    "set host header" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/virtualhost")
          .setVirtualHost("virtualhost:1337")
          .get()
          .toScala
          .map(_.getBody must be_==("virtualhost:1337"))
          .awaitFor(defaultTimeout)
      }
    }

    "complete after timeout" in {
      withClient() { client =>
        val requestWithoutTimeout = client.url(s"http://localhost:$testServerPort/timeout")
        requestWithoutTimeout.getRequestTimeoutDuration must be_==(Duration.ZERO)

        val requestWithTimeout = requestWithoutTimeout
          .setRequestTimeout(Duration.ofMillis(100))
        requestWithTimeout.getRequestTimeoutDuration must be_==(Duration.ofMillis(100))

        requestWithTimeout
          .get()
          .toScala
          .map(_ => failure)
          .recover {
            case ex =>
              // due to java/scala conversions of future, the exception
              // gets wrapped in CompletionException which we here unwrap
              val e = if (ex.getCause != null) ex.getCause else ex
              e must beAnInstanceOf[TimeoutException]
              e.getMessage must startWith("Request timeout")
              success
          }
          .awaitFor(defaultTimeout)
      }
    }

    "provide response status text" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/204")
          .get()
          .toScala
          .map(_.getStatusText must be_==("No Content"))
          .awaitFor(defaultTimeout)
      }
    }

    "allow access body more than one time" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/stream")
          .get()
          .toScala
          .map { resp =>
            resp.getBody must beEqualTo("streamed")
            resp.getBody must beEqualTo("streamed")
          }
          .awaitFor(defaultTimeout)
      }
    }

    "send and receive cookies" in {
      val cookie1 = new WSCookieBuilder().setName("cookie1").setValue("cookie1").build()
      val cookie2 = new WSCookieBuilder().setName("cookie2").setValue("cookie2").build()
      val cookie3 = new WSCookieBuilder().setName("cookie3").setValue("cookie3").build()
      val cookie4 = new WSCookieBuilder().setName("cookie4").setValue("cookie4").build()
      val cookie5 = new WSCookieBuilder().setName("cookie5").setValue("cookie5").build()
      def toTuple(c: WSCookie) = (c.getName, c.getValue)
      withClient() {
        _.url(s"http://localhost:$testServerPort/cookies")
          .addCookie(cookie5)
          .setCookies(Seq(cookie1).asJava)
          .addCookie(cookie2)
          .addCookies(cookie4)
          .get()
          .toScala
          .map { resp =>
            resp.getCookies.asScala.map(toTuple) must containTheSameElementsAs(Seq(cookie1, cookie2, cookie3, cookie4).map(toTuple))
            resp.getCookie(cookie1.getName).asScala.map(toTuple) must be_==(Some(cookie1).map(toTuple))
          }
          .awaitFor(defaultTimeout)
      }
    }
  }
}
