/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Future

class ScalaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification {

  "the client" should {

    "call out to a remote system and get a correct status" in {
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()
      val wsClient = StandaloneAhcWSClient()

      def call(wsClient: StandaloneWSClient): Future[Result] = {
        wsClient.url("http://www.google.com").get().map { response ⇒
          response.status must be_==(200)
        }
      }

      call(wsClient)
        .andThen { case _ => wsClient.close() }
        .andThen { case _ => system.terminate() }.await
    }

  }

}
