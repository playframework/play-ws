/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.akkahttp

import com.typesafe.sslconfig.akka.AkkaSSLConfig
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.{ StandaloneWSClient, WSClientConfig, WSClientConfigSpec }

class AkkaHttpWSClientConfigSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientConfigSpec {
  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result = {
    val config = configTransform(WSClientConfig.forConfig())
    val httpsContext = clientHttpsContext(Some(AkkaSSLConfig().withSettings(config.ssl)))
    val client = StandaloneAkkaHttpWSClient(httpsContext, config)(system, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
