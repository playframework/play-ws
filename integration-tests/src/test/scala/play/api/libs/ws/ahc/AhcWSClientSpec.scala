/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import akka.http.scaladsl.model.StatusCodes.Redirection
import akka.http.scaladsl.model.headers.{ HttpCookie, RawHeader }
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ MissingCookieRejection, Route }
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.specs2.concurrent.{ ExecutionEnv, FutureAwait }
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.api.libs.ws._
import play.shaded.ahc.org.asynchttpclient.handler.MaxRedirectException

import scala.concurrent._

class AhcWSClientSpec(implicit val executionEnv: ExecutionEnv) extends Specification
  with AkkaServerProvider
  with StandaloneWSClientSupport
  with FutureMatchers
  with FutureAwait
  with DefaultBodyReadables
  with DefaultBodyWritables {

  def withClientFollowingRedirect(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Result): Result = {
    withClient(
      config.copy(
        wsClientConfig = config.wsClientConfig.copy(followRedirects = true)
      )
    )(block)
  }

  val indexRoutes: Route = {
    path("index") {
      extractRequest { request =>
        respondWithHeaders(request.headers.map(h => RawHeader(s"Req-${h.name}", h.value))) {
          get {
            complete("Say hello to akka-http")
          } ~
            post {
              complete(s"POST: ${request.entity}")
            }
        }
      }
    }
  }

  val cookieRoutes: Route = {
    path("cookie") {
      get {
        setCookie(HttpCookie("flash", "redirect-cookie")) {
          redirect("/cookie-destination", StatusCodes.MovedPermanently)
        }
      }
    } ~
      path("cookie-destination") {
        get {
          optionalCookie("flash") {
            case Some(c) => complete(s"Cookie value => ${c.value}")
            case None => reject(MissingCookieRejection("flash"))
          }
        }
      }
  }

  val redirectRoutes: Route = {
    // Single redirect
    path("redirect" / IntNumber) { status =>
      get {
        val redirectCode = StatusCode.int2StatusCode(status).asInstanceOf[Redirection]
        redirect("/index", redirectCode)
      } ~
        post {
          val redirectCode = StatusCode.int2StatusCode(status).asInstanceOf[Redirection]
          redirect("/index", redirectCode)
        }
    } ~
      path("redirects" / IntNumber / IntNumber) { (status, count) =>
        get {
          val redirectCode = StatusCode.int2StatusCode(status).asInstanceOf[Redirection]
          if (status == 1) redirect("/index", redirectCode)
          else redirect(s"/redirects/$status/${count - 1}", redirectCode)
        }
      }
  }

  override val routes: Route = indexRoutes ~ cookieRoutes ~ redirectRoutes

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
        result must beEqualTo("Say hello to akka-http")
      }
    }

    "request a url as a Foo" in {
      case class Foo(body: String)

      implicit val fooBodyReadable: BodyReadable[Foo] = BodyReadable[Foo] { response =>
        import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }
        val ahcResponse = response.asInstanceOf[StandaloneAhcWSResponse].underlying[AHCResponse]
        Foo(ahcResponse.getResponseBody)
      }

      withClient() { client =>
        val result = Await.result(client.url(s"http://localhost:$testServerPort/index").get().map(res => res.body[Foo]), defaultTimeout)
        result must beEqualTo(Foo("Say hello to akka-http"))
      }
    }

    "request a url as a stream" in {
      withClient() { client =>
        val resultSource = Await.result(client.url(s"http://localhost:$testServerPort/index").stream().map(_.bodyAsSource), defaultTimeout)
        val bytes: ByteString = Await.result(resultSource.runWith(Sink.head), defaultTimeout)
        bytes.utf8String must beEqualTo("Say hello to akka-http")
      }
    }

    "when following redirect" in {

      "honor the number of redirects allowed" in {
        // 1. Default number of max redirects is 5
        withClientFollowingRedirect() { client =>
          {
            val request = client
              // 2. Ask to redirect 10 times
              .url(s"http://localhost:$testServerPort/redirects/302/10")
              .get()
            Await.result(request, defaultTimeout)
          } must throwA[MaxRedirectException]
        }
      }

      "should follow a redirect when configured to" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/302").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "should not follow redirect if client is configured not to" in {
        val wsConfig = WSClientConfig().copy(followRedirects = false)
        val ahcWsConfig = AhcWSClientConfigFactory.forConfig().copy(wsClientConfig = wsConfig)
        withClient(config = ahcWsConfig) { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/302").get(), defaultTimeout)
          result.status must beEqualTo(302)
        }
      }

      "should not follow redirect if request is configured not to" in {
        // 1. Client is configured to follow the redirect
        withClientFollowingRedirect() { client =>
          val request = client
            .url(s"http://localhost:$testServerPort/redirect/302")
            // 2. But turn off follow redirects for this request
            .withFollowRedirects(false)
            .get()
          val result = Await.result(request, defaultTimeout)
          result.status must beEqualTo(302)
        }
      }

      "follow redirect for HTTP 301 Moved Permanently" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/301").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "follow redirect for HTTP 302 Found" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/302").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "follow redirect for HTTP 303 See Other" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/303").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "follow redirect for HTTP 307 Temporary Redirect" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/307").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "follow redirect for HTTP 308 Permanent Redirect" in {
        withClientFollowingRedirect() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/redirect/308").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo("Say hello to akka-http")
        }
      }

      "keep virtual host" in {
        withClientFollowingRedirect() { client =>
          val request = client
            .url(s"http://localhost:$testServerPort/redirect/303")
            .withVirtualHost("localhost1")
            .get()
          val result = Await.result(request, defaultTimeout)
          result.header("Req-Host") must beSome("localhost1")
        }
      }

      "keep http headers" in {
        withClientFollowingRedirect() { client =>
          val request = client
            .url(s"http://localhost:$testServerPort/redirect/303")
            .addHttpHeaders("X-Test" -> "Test")
            .get()
          val result = Await.result(request, defaultTimeout)
          result.header("Req-X-Test") must beSome("Test")
        }
      }

      "keep auth information" in {
        withClientFollowingRedirect() { client =>
          val request = client
            .url(s"http://localhost:$testServerPort/redirect/303")
            .withAuth("test", "test", WSAuthScheme.BASIC)
            .get()
          val result = Await.result(request, defaultTimeout)
          result.header("Req-Authorization") must beSome
        }
      }

      "keep cookie when following redirect automatically and cookie store is configured" in {
        withClientFollowingRedirect(AhcWSClientConfigFactory.forConfig().copy(useCookieStore = true)) { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/cookie").get().map(res => res.body[String]), defaultTimeout)
          result must beEqualTo(s"Cookie value => redirect-cookie")
        }
      }

      "not keep cookie when repeating the request" in {
        withClientFollowingRedirect() { client =>
          // First let's do a request that sets a cookie
          val res1 = Await.result(client.url(s"http://localhost:$testServerPort/cookie").get().map(res => res.body[String]), defaultTimeout)

          // Then run a request to a url that checks the cookie, but without setting it
          val res2 = Await.result(client.url(s"http://localhost:$testServerPort/cookie-destination").get().map(res => res.body[String]), defaultTimeout)
          res2 must beEqualTo(s"Request is missing required cookie 'flash'")
        }
      }

      "switch to get " in {
        "for HTTP 301 Moved Permanently" in {
          withClientFollowingRedirect() { client =>
            val request = client
              .url(s"http://localhost:$testServerPort/redirect/301")
              // 1. We are doing a POST to a URL that will redirect to a path
              // That only accepts GETs
              .post("request body")

            val result = Await.result(request.map(res => res.body[String]), defaultTimeout)

            // 2. So when following the redirect, the GET path should be found
            // and we get its body
            result must beEqualTo("Say hello to akka-http")
          }
        }

        "for HTTP 303 See Other" in {
          withClientFollowingRedirect() { client =>
            val request = client
              .url(s"http://localhost:$testServerPort/redirect/303")
              .post("request body")
            val result = Await.result(request.map(res => res.body[String]), defaultTimeout)
            result must beEqualTo("Say hello to akka-http")
          }
        }

        "for HTTP 302 Found and not strict handling" in {
          withClientFollowingRedirect() { client =>
            val request = client
              .url(s"http://localhost:$testServerPort/redirect/302")
              // 1. We are doing a POST to a URL that will redirect to a path
              // That only accepts GETs
              .post("request body")

            val result = Await.result(request.map(res => res.body[String]), defaultTimeout)

            // 2. So when following the redirect, the GET path should be found
            // and we get its body
            result must beEqualTo("Say hello to akka-http")
          }
        }
      }
    }

  }
}
