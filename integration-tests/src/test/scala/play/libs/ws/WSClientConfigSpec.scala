/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.api.libs.ws.WSClientConfig

import scala.compat.java8.FutureConverters._

trait WSClientConfigSpec extends Specification
  with AkkaServerProvider {

  implicit def executionEnv: ExecutionEnv

  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result

  override val routes = play.api.libs.ws.WSClientConfigSpec.routes

  "WSClient" should {
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
  }

}
