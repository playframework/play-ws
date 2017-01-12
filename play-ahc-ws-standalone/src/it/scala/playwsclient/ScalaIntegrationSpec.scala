/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.ws.ahc.{ AhcConfigBuilder, StandaloneAhcWSClient }
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import scala.concurrent.duration._

class ScalaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification {

  "the client" should {

    "call out to a remote system and get a correct status" in {
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      val builder = new AhcConfigBuilder()
      val ahcClient = new DefaultAsyncHttpClient(builder.configure().build())
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
