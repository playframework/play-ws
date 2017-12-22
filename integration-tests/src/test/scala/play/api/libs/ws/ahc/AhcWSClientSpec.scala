package play.api.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.{ StandaloneWSClient, WSClientSpec }

class AhcWSClientSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val config = AhcWSClientConfigFactory.forConfig()
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
