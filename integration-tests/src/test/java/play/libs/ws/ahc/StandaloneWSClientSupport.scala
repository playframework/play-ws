/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import akka.stream.Materializer
import org.specs2.execute.Result
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClientConfig, AhcWSClientConfigFactory => ScalaAhcWSClientConfigFactory }
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

trait StandaloneWSClientSupport {

  def materializer: Materializer

  def withClient(config: AhcWSClientConfig = ScalaAhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Result): Result = {
    val asyncHttpClient = new DefaultAsyncHttpClient(new AhcConfigBuilder(config).build())
    val client = new StandaloneAhcWSClient(asyncHttpClient, materializer)
    try {
      block(client)
    } finally {
      client.close()
    }
  }
}
