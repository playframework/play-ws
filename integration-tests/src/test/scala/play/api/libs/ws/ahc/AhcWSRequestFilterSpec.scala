/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws._
import play.api.mvc.Results

import scala.collection.mutable

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with FutureMatchers
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
          response.body[String] must contain("some string")
        }
        .await(retries = 0, timeout = defaultTimeout)
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
          response.body[String] must contain("some string")
        }
        .await(retries = 0, timeout = defaultTimeout)
    }

    "work with one request filter" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .get()
        .map { _ =>
          callList must contain(1)
        }
        .await(retries = 0, timeout = defaultTimeout)
    }

    "stream with one request filter" in withClient() { client =>
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withMethod("GET")
        .stream()
        .map { _ =>
          callList must contain(1)
        }
        .await(retries = 0, timeout = defaultTimeout)
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
          callList must containTheSameElementsAs(Seq(1, 2, 3))
        }
        .await(retries = 0, timeout = defaultTimeout)
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
          callList must containTheSameElementsAs(Seq(1, 2, 3))
        }
        .await(retries = 0, timeout = defaultTimeout)
    }

    "should allow filters to modify the request" in withClient() { client =>
      val appendedHeader      = "X-Request-Id"
      val appendedHeaderValue = "someid"
      client
        .url(s"http://localhost:$testServerPort")
        .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get()
        .map { response =>
          response.headers("X-Request-Id").head must be_==("someid")
        }
        .await(retries = 0, timeout = defaultTimeout)
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
          response.headers("X-Request-Id").head must be_==("someid")
        }
        .await(retries = 0, timeout = defaultTimeout)
    }
  }
}
