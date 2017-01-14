/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.ws.{ WSRequestExecutor, WSRequestFilter }
import play.api.libs.ws.ahc.{ AhcWSClientConfigFactory, StandaloneAhcWSClient, StandaloneAhcWSRequest, StandaloneAhcWSResponse }

import scala.concurrent.Future
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

  class CallbackRequestFilter(callList: scala.collection.mutable.Buffer[Int], value: Int) extends WSRequestFilter[StandaloneAhcWSRequest, StandaloneAhcWSResponse] {
    override def apply(executor: WSRequestExecutor[StandaloneAhcWSRequest, StandaloneAhcWSResponse]): WSRequestExecutor[StandaloneAhcWSRequest, StandaloneAhcWSResponse] = {
      callList.append(value)
      executor
    }
  }

  class HeaderAppendingFilter(key: String, value: String) extends WSRequestFilter[StandaloneAhcWSRequest, StandaloneAhcWSResponse] {
    override def apply(next: WSRequestExecutor[StandaloneAhcWSRequest, StandaloneAhcWSResponse]): WSRequestExecutor[StandaloneAhcWSRequest, StandaloneAhcWSResponse] = {
      new WSRequestExecutor[StandaloneAhcWSRequest, StandaloneAhcWSResponse] {
        override def execute(request: StandaloneAhcWSRequest): Future[StandaloneAhcWSResponse] = {
          next.execute(request.withHeaders((key, value)).asInstanceOf[StandaloneAhcWSRequest])
        }
      }
    }
  }
  //
  //  "work with one request filter" in new WithServer() {
  //    val client = app.injector.instanceOf(classOf[WSClient])
  //    val callList = scala.collection.mutable.ArrayBuffer[Int]()
  //    val responseFuture = client.url(s"http://example.com:$testServerPort")
  //      .withRequestFilter(new CallbackRequestFilter(callList, 1))
  //      .get()
  //    callList must contain(1)
  //  }
  //
  //  "work with three request filter" in new WithServer() {
  //    val client = app.injector.instanceOf(classOf[WSClient])
  //    val callList = scala.collection.mutable.ArrayBuffer[Int]()
  //    val responseFuture = client.url(s"http://localhost:${testServerPort}")
  //      .withRequestFilter(new CallbackRequestFilter(callList, 1))
  //      .withRequestFilter(new CallbackRequestFilter(callList, 2))
  //      .withRequestFilter(new CallbackRequestFilter(callList, 3))
  //      .get()
  //    callList must containTheSameElementsAs(Seq(1, 2, 3))
  //  }
  //
  //  "should allow filters to modify the request" in {
  //    val appendedHeader = "key"
  //    val appendedHeaderValue = "value"
  //
  //    Server.withRouter() {
  //      case play.api.routing.sird.GET(p"/") => Action {
  //        request =>
  //          request.headers.get(appendedHeader) match {
  //            case Some(appendedHeaderValue) => Results.Ok
  //            case _ => Results.Forbidden
  //          }
  //      }
  //    } { implicit port =>
  //      implicit val materializer = Play.current.materializer
  //      WsTestClient.withClient { client =>
  //        val response = Await.result(
  //          client.url("/")
  //            .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
  //            .get(), 5.seconds)
  //        response.status must beEqualTo(200)
  //      }
  //    }
  //  }
}
