/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.AkkaServerProvider
import play.api.libs.ws._

import scala.collection.mutable

class AhcWSRequestFilterSpec(implicit val executionEnv: ExecutionEnv) extends Specification with AkkaServerProvider with AfterAll with FutureMatchers {

  // Create the standalone WS client
  val client = StandaloneAhcWSClient()

  override val routes: Route = {
    import akka.http.scaladsl.server.Directives._
    headerValueByName("X-Request-Id") { value =>
      respondWithHeader(RawHeader("X-Request-Id", value)) {
        val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
        complete(httpEntity)
      }
    } ~ {
      get {
        parameters('key.as[String]) { (key) =>
          val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Say hello to akka-http, key = $key</h1>")
          complete(httpEntity)
        }
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    client.close()
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
        WSRequestExecutor(r => executor(r.withHeaders((key, value))))
      }
    }

    "work with adhoc request filter" in {
      client.url(s"http://localhost:$testServerPort").withRequestFilter(WSRequestFilter { e =>
        WSRequestExecutor(r => e.apply(r.withQueryString("key" -> "some string")))
      }).get().map { response =>
        response.body must contain("some string")
      }.await(retries = 0, timeout = defaultTimeout)
    }

    "work with one request filter" in {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client.url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .get().map { _ =>
          callList must contain(1)
        }
        .await(retries = 0, timeout = defaultTimeout)
    }

    "work with three request filter" in {
      val callList = scala.collection.mutable.ArrayBuffer[Int]()
      client.url(s"http://localhost:$testServerPort")
        .withRequestFilter(new CallbackRequestFilter(callList, 1))
        .withRequestFilter(new CallbackRequestFilter(callList, 2))
        .withRequestFilter(new CallbackRequestFilter(callList, 3))
        .get().map { _ =>
          callList must containTheSameElementsAs(Seq(1, 2, 3))
        }
        .await(retries = 0, timeout = defaultTimeout)
    }

    "should allow filters to modify the request" in {
      val appendedHeader = "X-Request-Id"
      val appendedHeaderValue = "someid"
      client.url(s"http://localhost:$testServerPort")
        .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
        .get().map { response ⇒
          response.allHeaders("X-Request-Id").head must be_==("someid")
        }
        .await(retries = 0, timeout = defaultTimeout)
    }
  }
}
