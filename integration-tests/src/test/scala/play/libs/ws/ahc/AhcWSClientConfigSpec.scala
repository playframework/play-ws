package play.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.WSClientConfig
import play.libs.ws.{ StandaloneWSClient, WSClientConfigSpec }

class AhcWSClientConfigSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientConfigSpec {
  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result = {
    val config = AhcWSClientConfigFactory.forClientConfig(configTransform(WSClientConfig()))
    val client = StandaloneAhcWSClient.create(config, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
