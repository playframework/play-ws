/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.ws.WSConfigParser
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClientConfigParser, StandaloneAhcWSClient }
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import scala.concurrent.duration._

class ScalaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification {

  "the client" should {

    "call out to a remote system and get a correct status" in {

      // Create a config from the application.conf file
      val config = ConfigFactory.load
      val classLoader = this.getClass.getClassLoader
      val wsClientConfig = new WSConfigParser(config, classLoader).parse
      val ahcWSClientConfig = new AhcWSClientConfigParser(wsClientConfig, config, classLoader).parse

      // Map the WS config to the AHC config class.
      val builder = new AhcConfigBuilder(ahcWSClientConfig)
      val asyncHttpClientConfigBuilder = builder.configure()
      val asyncHttpClientConfig = asyncHttpClientConfigBuilder.build()

      // Create the AHC client
      val ahcClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)

      // Create Akka system for thread and streaming management
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      // Create the standalone WS client
      val wsClient = new StandaloneAhcWSClient(ahcClient)

      wsClient.url("http://www.google.com").get().map { response ⇒
        response.status must be_==(200)
      }.andThen {
        case _ => wsClient.close()
      }.andThen {
        case _ => system.terminate()
      }.await(retries = 0, timeout = 5.seconds)
    }

  }

}
