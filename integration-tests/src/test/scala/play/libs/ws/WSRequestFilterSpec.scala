/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.libs.ws.ahc.{ CallbackRequestFilter, HeaderAppendingFilter }

import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._

trait WSRequestFilterSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers {

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

  override val routes = play.api.libs.ws.WSRequestFilterSpec.routes

  "with request filters" should {

    "execute with adhoc request filter" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new WSRequestFilter {
            override def apply(ex: WSRequestExecutor) = new WSRequestExecutor {
              override def apply(r: StandaloneWSRequest) = ex.apply(r.addQueryParameter("key", "some string"))
            }
          })
          .get()
          .toScala
          .map(_.getBody must contain("some string"))
          .awaitFor(defaultTimeout)
      }
    }

    "stream with adhoc request filter" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new WSRequestFilter {
            override def apply(ex: WSRequestExecutor) = new WSRequestExecutor {
              override def apply(r: StandaloneWSRequest) = ex.apply(r.addQueryParameter("key", "some string"))
            }
          })
          .setMethod("GET")
          .stream()
          .toScala
          .map(_.getBody must contain("some string"))
          .awaitFor(defaultTimeout)
      }
    }

    "execute with one request filter" in {
      val callList = new java.util.ArrayList[Integer]()
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .get()
          .toScala
          .map(_ => callList.asScala must contain(1))
          .awaitFor(defaultTimeout)
      }
    }

    "stream with one request filter" in {
      val callList = new java.util.ArrayList[Integer]()
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setMethod("GET")
          .stream()
          .toScala
          .map(_ => callList.asScala must contain(1))
          .awaitFor(defaultTimeout)
      }
    }

    "execute with three request filters" in {
      val callList = new java.util.ArrayList[Integer]()
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setRequestFilter(new CallbackRequestFilter(callList, 2))
          .setRequestFilter(new CallbackRequestFilter(callList, 3))
          .get()
          .toScala
          .map(_ => callList.asScala must containTheSameElementsAs(Seq(1, 2, 3)))
          .awaitFor(defaultTimeout)
      }
    }

    "stream with three request filters" in {
      val callList = new java.util.ArrayList[Integer]()
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new CallbackRequestFilter(callList, 1))
          .setRequestFilter(new CallbackRequestFilter(callList, 2))
          .setRequestFilter(new CallbackRequestFilter(callList, 3))
          .setMethod("GET")
          .stream()
          .toScala
          .map(_ => callList.asScala must containTheSameElementsAs(Seq(1, 2, 3)))
          .awaitFor(defaultTimeout)
      }
    }

    "allow filters to modify the executing request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .get()
          .toScala
          .map(_.getSingleHeader(appendedHeader).get must be_==(appendedHeaderValue))
          .awaitFor(defaultTimeout)
      }
    }

    "allow filters to modify the streaming request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .setRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .setMethod("GET")
          .stream()
          .toScala
          .map(_.getSingleHeader(appendedHeader).get must be_==(appendedHeaderValue))
          .awaitFor(defaultTimeout)
      }
    }

  }

}
