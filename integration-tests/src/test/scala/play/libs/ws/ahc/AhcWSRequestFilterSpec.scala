/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.mvc.Results

import scala.concurrent.duration._
import scala.jdk.FutureConverters._

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with FutureMatchers {

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
      val callList       = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .get()
          .asScala
      responseFuture
        .map { _ =>
          callList.asScala must contain(1)
        }
        .await(retries = 0, timeout = 5.seconds)
    }

    "work with three request filter" in withClient() { client =>
      import scala.jdk.CollectionConverters._
      val callList       = new java.util.ArrayList[Integer]()
      val responseFuture =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setRequestFilter(new CallbackRequestFilter(callList, 2))
          .setRequestFilter(new CallbackRequestFilter(callList, 3))
          .get()
          .asScala
      responseFuture
        .map { _ =>
          callList.asScala must containTheSameElementsAs(Seq(1, 2, 3))
        }
        .await(retries = 0, timeout = 5.seconds)
    }

    "should allow filters to modify the request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      val responseFuture      =
        client
          .url(s"http://localhost:$testServerPort")
          .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .get()
          .asScala

      responseFuture
        .map { response =>
          response.getHeaders.get("X-Request-Id").get(0) must be_==("someid")
        }
        .await(retries = 0, timeout = 5.seconds)
    }
  }
}
