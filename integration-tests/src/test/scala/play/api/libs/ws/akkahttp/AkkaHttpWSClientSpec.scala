/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.akkahttp

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws.{ StandaloneWSClient, WSClientSpec }

class AkkaHttpWSClientSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val client = StandaloneAkkaHttpWSClient(clientHttpsContext())(system, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
