/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.akkahttp

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws._

class AkkaHttpWSRequestFilterSpec(implicit override val executionEnv: ExecutionEnv) extends WSRequestFilterSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val client = StandaloneAkkaHttpWSClient()
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
