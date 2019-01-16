/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv) extends Specification with AkkaServerProvider with StandaloneWSClientSupport with FutureMatchers {

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

  "setRequestFilter" should {

    "work with one request filter" in withClient() { client =>
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .get())
      responseFuture.map { _ =>
        callList.asScala must contain(1)
      }.await(retries = 0, timeout = 5.seconds)
    }

    "stream with one request filter" in withClient() { client =>
      import scala.collection.JavaConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new CallbackRequestFilter(callList, 1))
        .stream())
      responseFuture.map { _ =>
        callList.asScala must contain(1)
      }.await(retries = 0, timeout = 5.seconds)
    }

    "work with three request filter" in withClient() { client =>
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

    "stream with three request filters" in withClient() { client =>
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

    "should allow filters to modify the request" in withClient() { client =>
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture = FutureConverters.toScala(client.url(s"http://localhost:$testServerPort")
        .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get())

      responseFuture.map { response =>
        response.getHeaders.get("X-Request-Id").get(0) must be_==("someid")
      }.await(retries = 0, timeout = 5.seconds)
    }

    "allow filters to modify the streaming request" in withClient() { client =>
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
