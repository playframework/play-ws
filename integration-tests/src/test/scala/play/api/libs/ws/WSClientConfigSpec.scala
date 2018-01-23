package play.api.libs.ws

import akka.http.scaladsl.coding.{ Deflate, Gzip }
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
      }
  }
}

trait WSClientConfigSpec extends Specification
    with AkkaServerProvider {

  implicit def executionEnv: ExecutionEnv

  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result

  override val routes = WSClientConfigSpec.routes

  "WSClient" should {
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
  }

}
