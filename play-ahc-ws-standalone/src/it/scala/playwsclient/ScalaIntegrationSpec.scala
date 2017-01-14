/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package playwsclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion, RawHeader}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.{BeforeAfter, Specification}
import org.specs2.specification.{AfterAll, Scope}
import play.api.libs.ws.ahc.{AhcWSClientConfigFactory, StandaloneAhcWSClient}
import play.api.libs.ws.{StandaloneWSRequest, StandaloneWSResponse, WSRequestExecutor, WSRequestFilter}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class ScalaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers {

  sequential

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client
  val client = StandaloneAhcWSClient(
    AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)
  )

  override def afterAll = {
    client.close()
    system.terminate()
  }

  class WithServer extends Scope with BeforeAfter {
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.server.Directives._

    private var server: Http.ServerBinding = _

    final class RequestIdHeader(token: String) extends ModeledCustomHeader[RequestIdHeader] {
      override def renderInRequests = false
      override def renderInResponses = false
      override val companion = RequestIdHeader
      override def value: String = token
    }

    object RequestIdHeader extends ModeledCustomHeaderCompanion[RequestIdHeader] {
      override val name = "X-Request-Id"
      override def parse(value: String) = Try(new RequestIdHeader(value))
    }

    def before = {
      val route =
        headerValueByName("X-Request-Id") { value =>
          respondWithHeader(RawHeader("X-Request-Id", value)) {
            val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
            complete(httpEntity)
          }
        } ~ {
          val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
          complete(httpEntity)
        }
      server = Await.result(Http().bindAndHandle(route, "localhost", 8080), 5.seconds)
    }

    def after = {
      server.unbind()
    }
  }

  "the client" should {

    "call out to a remote system and get a correct status" in new WithServer {
      client.url("http://localhost:8080/").get().map { response ⇒
        response.status must be_==(200)
      }.await(retries = 0, timeout = 5.seconds)
    }

  }

  "with request filters" should {

    class CallbackRequestFilter[Req <: StandaloneWSRequest, Res <: StandaloneWSResponse](
      callList: mutable.Buffer[Int],
        value: Int) extends WSRequestFilter[Req, Res] {
      override def apply(executor: WSRequestExecutor[Req, Res]): WSRequestExecutor[Req, Res] = {
        callList.append(value)
        executor
      }
    }

    class HeaderAppendingFilter[Req <: StandaloneWSRequest, Res <: StandaloneWSResponse](key: String, value: String) extends WSRequestFilter[Req, Res] {
      override def apply(next: WSRequestExecutor[Req, Res]): WSRequestExecutor[Req, Res] = {
        new WSRequestExecutor[Req, Res] {
          override def execute(request: Req): Future[Res] = {
            next.execute(request.withHeaders((key, value)).asInstanceOf[Req])
          }
        }
      }
    }

    "work with one request filter" in new WithServer {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client.url("http://localhost:8080")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .get().map { _ =>
          callList must contain(1)
        }.await(retries = 0, timeout = 5.seconds)
    }

    "work with three request filter" in new WithServer {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client.url("http://localhost:8080")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withRequestFilter(new CallbackRequestFilter(callList, 2))
        .withRequestFilter(new CallbackRequestFilter(callList, 3))
        .get().map { _ =>
          callList must containTheSameElementsAs(Seq(1, 2, 3))
        }.await(retries = 0, timeout = 5.seconds)
    }

    "should allow filters to modify the request" in new WithServer {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      client.url(s"http://localhost:8080")
        .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get().map { response ⇒
          response.allHeaders("X-Request-Id").head must be_==("someid")
        }.await(retries = 0, timeout = 5.seconds)
    }
  }
}
