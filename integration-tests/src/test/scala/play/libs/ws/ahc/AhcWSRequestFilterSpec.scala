/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.AkkaServerProvider

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv) extends Specification with AkkaServerProvider with AfterAll with FutureMatchers {

  // Create the standalone WS client with no cache
  val client = StandaloneAhcWSClient.create(
    AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader),
    null,
    materializer
  )

  override val routes: Route = {
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

  override def afterAll(): Unit = {
    super.afterAll()
    client.close()
  }

  "setRequestFilter" should {

    "work with one request filter" in {
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .get())
      responseFuture.map { _ =>
        callList.asScala must contain(1)
      }.await(retries = 0, timeout = 5.seconds)
    }

    "work with three request filter" in {
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

    "should allow filters to modify the request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get())

      responseFuture.map { response =>
        response.getAllHeaders.get("X-Request-Id").get(0) must be_==("someid")
      }.await(retries = 0, timeout = 5.seconds)
    }
  }

}
