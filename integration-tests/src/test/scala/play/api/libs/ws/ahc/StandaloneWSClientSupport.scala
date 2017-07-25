/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.stream.Materializer
import org.specs2.execute.Result

trait StandaloneWSClientSupport {

  def materializer: Materializer

  def withClient(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Result): Result = {
    val client = StandaloneAhcWSClient(config)(materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
