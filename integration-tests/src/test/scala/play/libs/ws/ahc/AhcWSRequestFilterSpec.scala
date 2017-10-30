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

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters

class AhcWSRequestFilterSpec(implicit executionEnv: ExecutionEnv) extends Specification with AfterAll with FutureMatchers {
  val testServerPort = 49134

  sequential

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client with no cache
  private val client = StandaloneAhcWSClient.create(
    AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader),
    null,
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

  override def afterAll: Unit = {
    futureServer.foreach(_.unbind)
    client.close()
    system.terminate()
  }

  "setRequestFilter" should {

    "execute with one request filter" in {
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .get())
      responseFuture.map { _ =>
        callList.asScala must contain(1)
      }.await(retries = 0, timeout = 5.seconds)
    }

    "stream with one request filter" in {
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .stream())
      responseFuture.map { _ =>
        callList.asScala must contain(1)
      }.await(retries = 0, timeout = 5.seconds)
    }

    "execute with three request filter" in {
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .setRequestFilter(new CallbackRequestFilter(callList, 2))
        .setRequestFilter(new CallbackRequestFilter(callList, 3))
        .get())
      responseFuture.map { _ =>
        callList.asScala must containTheSameElementsAs(Seq(1, 2, 3))
      }.await(retries = 0, timeout = 5.seconds)
    }

    "stream with three request filters" in {
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .setRequestFilter(new CallbackRequestFilter(callList, 2))
        .setRequestFilter(new CallbackRequestFilter(callList, 3))
        .stream())
      responseFuture.map { _ =>
        callList.asScala must containTheSameElementsAs(Seq(1, 2, 3))
      }.await(retries = 0, timeout = 5.seconds)
    }

    "allow filters to modify the executing request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get())

      responseFuture.map { response =>
        response.getHeaders.get("X-Request-Id").get(0) must be_==("someid")
      }.await(retries = 0, timeout = 5.seconds)
    }

    "allow filters to modify the streaming request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .stream())

      responseFuture.map { response =>
        response.getHeaders.get("X-Request-Id").get(0) must be_==("someid")
      }.await(retries = 0, timeout = 5.seconds)
    }
  }
}
