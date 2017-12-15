package play.libs.ws.akkahttp

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.libs.ws.{ StandaloneWSClient, WSRequestFilterSpec }

class AkkaHttpWSRequestFilterSpec(implicit override val executionEnv: ExecutionEnv) extends WSRequestFilterSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val client = new StandaloneAkkaHttpWSClient(system, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
