/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.apache.pekko.stream.Materializer
import play.api.libs.ws.ahc.AhcConfigBuilder
import play.api.libs.ws.ahc.AhcWSClientConfig
import play.api.libs.ws.ahc.{ AhcWSClientConfigFactory => ScalaAhcWSClientConfigFactory }
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

trait StandaloneWSClientSupport {

  def materializer: Materializer

  def withClient[A](
      config: AhcWSClientConfig = ScalaAhcWSClientConfigFactory.forConfig()
  )(block: StandaloneAhcWSClient => A): A = {
    val asyncHttpClient = new DefaultAsyncHttpClient(new AhcConfigBuilder(config).build())
    val client          = new StandaloneAhcWSClient(asyncHttpClient, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
