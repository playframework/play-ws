/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.apache.pekko.stream.Materializer
import org.specs2.execute.Result

trait StandaloneWSClientSupport {

  def materializer: Materializer

  def withClient(
      config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig()
  )(block: StandaloneAhcWSClient => Result): Result = {
    val client = StandaloneAhcWSClient(config)(materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
