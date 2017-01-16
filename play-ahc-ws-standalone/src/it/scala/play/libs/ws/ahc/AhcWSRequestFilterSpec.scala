/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.libs.ws._

import scala.compat.java8.FutureConverters

class AhcWSRequestFilterSpec(implicit executionEnv: ExecutionEnv) extends Specification with AfterAll with FutureMatchers {
  val testServerPort = 49134

  sequential

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client
  val client = StandaloneAhcWSClient.create(
    AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader),
    materializer
  )

  private val route = {
    import akka.http.scaladsl.server.Directives._
    headerValueByName("X-Request-Id") { value =>
      respondWithHeader(RawHeader("X-Request-Id", value)) {
        val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
        complete(httpEntity)
      }
    } ~ {
      val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
      complete(httpEntity)
    }
  }

  private val futureServer = {
    Http().bindAndHandle(route, "localhost", testServerPort)
  }

  override def afterAll = {
    futureServer.foreach(_.unbind)
    client.close()
    system.terminate()
  }

  "setRequestFilter" should {

    class CallbackRequestFilter(callList: scala.collection.mutable.Buffer[Int], value: Int) extends WSRequestFilter {
      override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
        callList.append(value)
        executor
      }
    }

    "work with one request filter" in {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .get())
      responseFuture.map { _ =>
        callList must contain(1)
      }.await
    }

    "work with three request filter" in {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .setRequestFilter(new CallbackRequestFilter(callList, 2))
        .setRequestFilter(new CallbackRequestFilter(callList, 3))
        .get())
      responseFuture.map { _ =>
        callList must containTheSameElementsAs(Seq(1, 2, 3))
      }.await
    }

    "should allow filters to modify the request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get())

      responseFuture.map { response =>
        response.getAllHeaders.get("X-Request-Id").get(0) must be_==("someid")
      }.await
    }
  }

}
