/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.http.scaladsl.coding.{ Deflate, Gzip }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{ `Accept-Encoding`, `User-Agent` }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.AkkaServerProvider

object WSClientConfigSpec {
  val routes = {
    import akka.http.scaladsl.server.Directives._
    path("user-agent") {
      headerValueByType[`User-Agent`]() { ua =>
        complete(ua.products.head.toString())
      }
    } ~
      path("compression") {
        headerValueByType[`Accept-Encoding`]() { enc =>
          encodeResponseWith(Gzip, Deflate) {
            complete(enc.encodings.mkString(","))
          }
        }
      } ~
      path("redirect") {
        redirect("/redirected", MovedPermanently)
      } ~
      path("redirect-always") {
        redirect("/redirect-always", MovedPermanently)
      } ~
      path("redirected") {
        complete("OK")
      }
  }
}

trait WSClientConfigSpec extends Specification
  with AkkaServerProvider {

  implicit def executionEnv: ExecutionEnv

  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result

  override val routes = WSClientConfigSpec.routes

  "Scala Api WSClient" should {
    "use user agent from config" in {
      withClient(_.copy(userAgent = Some("custom-user-agent"))) {
        _.url(s"http://localhost:$testServerPort/user-agent")
          .get()
          .map(_.body)
          .map(_ must beEqualTo("custom-user-agent"))
          .awaitFor(defaultTimeout)
      }
    }

    "uncompress when compression enabled" in {
      withClient(_.copy(compressionEnabled = true)) {
        _.url(s"http://localhost:$testServerPort/compression")
          .get()
          .map(_.body)
          .map(_ must beEqualTo("gzip,deflate"))
          .awaitFor(defaultTimeout)
      }
    }

    "follow redirects by default" in {
      withClient(identity) { client =>
        val request = client.url(s"http://localhost:$testServerPort/redirect")
        request.followRedirects must beSome(true)

        request
          .get()
          .map(_.body)
          .map(_ must beEqualTo("OK"))
          .awaitFor(defaultTimeout)
      }
    }

    "follow redirects" in {
      withClient(_.copy(followRedirects = true)) {
        _.url(s"http://localhost:$testServerPort/redirect")
          .get()
          .map(_.body)
          .map(_ must beEqualTo("OK"))
          .awaitFor(defaultTimeout)
      }
    }

    "do not follow redirects" in {
      withClient(_.copy(followRedirects = false)) {
        _.url(s"http://localhost:$testServerPort/redirect")
          .get()
          .map(_.status)
          .map(_ must beEqualTo(MovedPermanently.intValue))
          .awaitFor(defaultTimeout)
      }
    }

    "follow redirects (request building trumps config)" in {
      withClient(_.copy(followRedirects = false)) {
        _.url(s"http://localhost:$testServerPort/redirect")
          .withFollowRedirects(true)
          .get()
          .map(_.body)
          .map(_ must beEqualTo("OK"))
          .awaitFor(defaultTimeout)
      }
    }

    "do not follow redirects (request building trumps config)" in {
      withClient(_.copy(followRedirects = true)) {
        _.url(s"http://localhost:$testServerPort/redirect")
          .withFollowRedirects(false)
          .get()
          .map(_.status)
          .map(_ must beEqualTo(MovedPermanently.intValue))
          .awaitFor(defaultTimeout)
      }
    }

    "eventually stop following perpetual redirecting" in {
      withClient(_.copy(followRedirects = true)) {
        _.url(s"http://localhost:$testServerPort/redirect-always")
          .get()
          .map(_ => failure)
          .recover {
            case ex =>
              ex.getMessage must contain("Maximum redirect reached")
              success
          }
          .awaitFor(defaultTimeout)
      }
    }
  }

}
