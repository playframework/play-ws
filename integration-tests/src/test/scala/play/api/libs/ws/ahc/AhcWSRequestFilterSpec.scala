/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.api.libs.ws._

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv) extends WSRequestFilterSpec {
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
