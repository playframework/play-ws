/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws._
import play.api.mvc.Results

import scala.collection.mutable

class AhcWSRequestFilterSpec
    extends AnyWordSpec
    with NettyServerProvider
    with StandaloneWSClientSupport
    with ScalaFutures
    with DefaultBodyReadables {

  override def routes(components: BuiltInComponents) = { case _ =>
    components.defaultActionBuilder { req =>
      req.headers.get("X-Request-Id") match {
        case Some(value) =>
          Results
            .Ok(
              <h1>Say hello to play</h1>
            )
            .withHeaders(("X-Request-Id", value))
        case None =>
          req.getQueryString("key") match {
            case Some(key) =>
              Results
                .Ok(
                  <h1>Say hello to play, key = ${key}</h1>
                )
            case None =>
              Results.NotFound
          }
      }
    }
  }

  "with request filters" should {

    class CallbackRequestFilter(callList: mutable.Buffer[Int], value: Int) extends WSRequestFilter {
      override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
        callList.append(value)
        executor
      }
    }

    class HeaderAppendingFilter(key: String, value: String) extends WSRequestFilter {
      override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
        WSRequestExecutor(r => executor(r.withHttpHeaders((key, value))))
      }
    }

    "work with adhoc request filter" in withClient() { client =>
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(WSRequestFilter { e =>
          WSRequestExecutor(r => e.apply(r.withQueryStringParameters("key" -> "some string")))
        })
        .get()
        .map { response =>
          assert(response.body[String].contains("some string"))
        }
        .futureValue
    }

    "stream with adhoc request filter" in withClient() { client =>
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(WSRequestFilter { e =>
          WSRequestExecutor(r => e.apply(r.withQueryStringParameters("key" -> "some string")))
        })
        .withMethod("GET")
        .stream()
        .map { response =>
          assert(response.body[String].contains("some string"))
        }
        .futureValue
    }

    "work with one request filter" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .get()
        .map { _ =>
          assert(callList.contains(1))
        }
        .futureValue
    }

    "stream with one request filter" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withMethod("GET")
        .stream()
        .map { _ =>
          assert(callList.contains(1))
        }
        .futureValue
    }

    "work with three request filter" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withRequestFilter(new CallbackRequestFilter(callList, 2))
        .withRequestFilter(new CallbackRequestFilter(callList, 3))
        .get()
        .map { _ =>
          assert(callList.toSet == Set(1, 2, 3))
        }
        .futureValue
    }

    "stream with three request filters" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withRequestFilter(new CallbackRequestFilter(callList, 2))
        .withRequestFilter(new CallbackRequestFilter(callList, 3))
        .withMethod("GET")
        .stream()
        .map { _ =>
          assert(callList.toSet == Set(1, 2, 3))
        }
        .futureValue
    }

    "should allow filters to modify the request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get()
        .map { response =>
          assert(response.headers("X-Request-Id").head == "someid")
        }
        .futureValue
    }

    "allow filters to modify the streaming request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .withMethod("GET")
        .stream()
        .map { response =>
          assert(response.headers("X-Request-Id").head == "someid")
        }
        .futureValue
    }
  }
}
