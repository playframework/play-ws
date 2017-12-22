/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import play.libs.ws._

class AhcWSClientSpec(implicit override val executionEnv: ExecutionEnv) extends WSClientSpec {
  def withClient()(block: StandaloneWSClient => Result): Result = {
    val config = AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)
    val client = StandaloneAhcWSClient.create(config, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
