/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.mvc.Results

import scala.jdk.FutureConverters._

class AhcWSRequestFilterSpec
    extends AnyWordSpec
    with NettyServerProvider
    with StandaloneWSClientSupport
    with ScalaFutures {

  override def routes(components: BuiltInComponents) = { case _ =>
    components.defaultActionBuilder { req =>
      val res = Results
        .Ok(
          <h1>Say hello to play</h1>
        )
      req.headers.get("X-Request-Id").fold(res) { value =>
        res.withHeaders(("X-Request-Id", value))
      }
    }
  }

  "setRequestFilter" should {

    "work with one request filter" in withClient() { client =>
      import scala.jdk.CollectionConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .get()
          .asScala
      responseFuture.map { _ =>
        assert(callList.asScala.map(_.intValue()).contains(1))
      }.futureValue
    }

    "stream with one request filter" in withClient() { client =>
      import scala.jdk.CollectionConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .stream()
          .asScala
      responseFuture.map { _ =>
        assert(callList.asScala.map(_.intValue()).contains(1))
      }.futureValue
    }

    "work with three request filter" in withClient() { client =>
      import scala.jdk.CollectionConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setRequestFilter(new CallbackRequestFilter(callList, 2))
          .setRequestFilter(new CallbackRequestFilter(callList, 3))
          .get()
          .asScala
      responseFuture.map { _ =>
        assert(callList.asScala.toSet == Set(1, 2, 3))
      }.futureValue
    }

    "stream with three request filters" in withClient() { client =>
      import scala.jdk.CollectionConverters._
      val callList = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setRequestFilter(new CallbackRequestFilter(callList, 2))
          .setRequestFilter(new CallbackRequestFilter(callList, 3))
          .stream()
          .asScala
      responseFuture.map { _ =>
        assert(callList.asScala.toSet == Set(1, 2, 3))
      }.futureValue
    }

    "should allow filters to modify the request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .get()
          .asScala

      responseFuture.map { response =>
        assert(response.getHeaders.get("X-Request-Id").get(0) == "someid")
      }.futureValue
    }

    "allow filters to modify the streaming request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .stream()
          .asScala

      responseFuture.map { response =>
        assert(response.getHeaders.get("X-Request-Id").get(0) == "someid")
      }.futureValue
    }
  }
}
