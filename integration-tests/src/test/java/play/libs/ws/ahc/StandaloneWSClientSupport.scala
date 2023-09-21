/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.apache.pekko.stream.Materializer
import org.specs2.execute.Result
import play.api.libs.ws.ahc.AhcConfigBuilder
import play.api.libs.ws.ahc.AhcWSClientConfig
import play.api.libs.ws.ahc.{ AhcWSClientConfigFactory => ScalaAhcWSClientConfigFactory }
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

trait StandaloneWSClientSupport {

  def materializer: Materializer

  def withClient(
      config: AhcWSClientConfig = ScalaAhcWSClientConfigFactory.forConfig()
  )(block: StandaloneAhcWSClient => Result): Result = {
    val asyncHttpClient = new DefaultAsyncHttpClient(new AhcConfigBuilder(config).build())
    val client          = new StandaloneAhcWSClient(asyncHttpClient, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
