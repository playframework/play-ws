package play.api.libs.ws

import akka.http.scaladsl.model.headers.`User-Agent`
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
  }

}
