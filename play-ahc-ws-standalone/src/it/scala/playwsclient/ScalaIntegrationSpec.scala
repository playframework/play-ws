/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.ws.ahc.{ AhcWSClientConfigFactory, StandaloneAhcWSClient }

import scala.concurrent.duration._

class ScalaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification {

  "the client" should {

    "call out to a remote system and get a correct status" in {
      // Create Akka system for thread and streaming management
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      // Create the standalone WS client
      val wsClient = StandaloneAhcWSClient(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)
      )

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
