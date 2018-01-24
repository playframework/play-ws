/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.{ AkkaServerProvider, PendingSupport }
import play.api.libs.ws.WSClientConfig

import scala.compat.java8.FutureConverters._

trait WSClientConfigSpec extends Specification
  with AkkaServerProvider
  with PendingSupport {

  implicit def executionEnv: ExecutionEnv

  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result

  override val routes = play.api.libs.ws.WSClientConfigSpec.routes

  "Java Api WSClient" should {
    "use user agent from config" in {
      withClient(_.copy(userAgent = Some("custom-user-agent"))) {
        _.url(s"http://localhost:$testServerPort/user-agent")
          .get()
          .toScala
          .map(_.getBody)
          .map(_ must beEqualTo("custom-user-agent"))
          .awaitFor(defaultTimeout)
      }
    }

    "uncompress when compression enabled" in {
      withClient(_.copy(compressionEnabled = true)) {
        _.url(s"http://localhost:$testServerPort/compression")
          .get()
          .toScala
          .map(_.getBody)
          .map(_ must beEqualTo("gzip,deflate"))
          .awaitFor(defaultTimeout)
      }
    }

    "follow redirects" in {
      withClient(_.copy(followRedirects = true)) { client =>
        pendingFor(Ahc(client), "Java Api does not follow redirects") {
          client.url(s"http://localhost:$testServerPort/redirect")
            .get()
            .toScala
            .map(_.getBody)
            .map(_ must beEqualTo("OK"))
            .awaitFor(defaultTimeout)
        }
      }
    }

    "eventually stop following perpetual redirecting" in {
      withClient(_.copy(followRedirects = true)) { client =>
        pendingFor(Ahc(client), "Java Api does not follow redirects") {
          client.url(s"http://localhost:$testServerPort/redirect-always")
            .get()
            .toScala
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

}
