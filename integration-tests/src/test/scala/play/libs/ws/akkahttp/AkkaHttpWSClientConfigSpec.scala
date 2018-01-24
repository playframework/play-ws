/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.akkahttp

import com.typesafe.sslconfig.akka.AkkaSSLConfig
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.WSClientConfig
import play.libs.ws.{ StandaloneWSClient, WSClientConfigSpec }

class AkkaHttpWSClientConfigSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientConfigSpec {
  def withClient(configTransform: WSClientConfig => WSClientConfig)(block: StandaloneWSClient => Result): Result = {
    val config = configTransform(WSClientConfig.forConfig())
    val httpsContext = clientHttpsContext(Some(AkkaSSLConfig().withSettings(config.ssl)))
    val client = new StandaloneAkkaHttpWSClient(system, materializer, httpsContext, config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
