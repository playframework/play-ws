/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.{ StandaloneWSClient, WSClientConfig, WSClientConfigSpec }

class AhcWSClientConfigSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientConfigSpec {
  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result = {
    val config = AhcWSClientConfigFactory.forClientConfig(configTransform(WSClientConfig()))
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
