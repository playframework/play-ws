/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import org.specs2.execute.Result
import play.AkkaServerProvider

import scala.collection.mutable

object WSRequestFilterSpec {

  val routes: Route = {
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
}

trait WSRequestFilterSpec extends Specification with AkkaServerProvider with AfterAll with FutureMatchers {
  import DefaultBodyReadables._

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

  override val routes = WSRequestFilterSpec.routes

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

    "execute with adhoc request filter" in {
      withClient() { client =>
        client.url(s"http://localhost:$testServerPort").withRequestFilter(WSRequestFilter { e =>
          WSRequestExecutor(r => e.apply(r.withQueryStringParameters("key" -> "some string")))
        }).get().map { response =>
          response.body[String] must contain("some string")
        }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "stream with adhoc request filter" in {
      withClient() { client =>
        client.url(s"http://localhost:$testServerPort").withRequestFilter(WSRequestFilter { e =>
          WSRequestExecutor(r => e.apply(r.withQueryStringParameters("key" -> "some string")))
        }).withMethod("GET").stream().map { response =>
          response.body[String] must contain("some string")
        }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "execute with one request filter" in {
      withClient() { client =>
        val callList = scala.collection.mutable.ArrayBuffer[Int]()
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new CallbackRequestFilter(callList, 1))
          .get().map { _ =>
            callList must contain(1)
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "stream with one request filter" in {
      withClient() { client =>
        val callList = scala.collection.mutable.ArrayBuffer[Int]()
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new CallbackRequestFilter(callList, 1))
          .withMethod("GET").stream().map { _ =>
            callList must contain(1)
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "execute with three request filters" in {
      withClient() { client =>
        val callList = scala.collection.mutable.ArrayBuffer[Int]()
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new CallbackRequestFilter(callList, 1))
          .withRequestFilter(new CallbackRequestFilter(callList, 2))
          .withRequestFilter(new CallbackRequestFilter(callList, 3))
          .get().map { _ =>
            callList must containTheSameElementsAs(Seq(1, 2, 3))
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "stream with three request filters" in {
      withClient() { client =>
        val callList = scala.collection.mutable.ArrayBuffer[Int]()
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new CallbackRequestFilter(callList, 1))
          .withRequestFilter(new CallbackRequestFilter(callList, 2))
          .withRequestFilter(new CallbackRequestFilter(callList, 3))
          .withMethod("GET").stream().map { _ =>
            callList must containTheSameElementsAs(Seq(1, 2, 3))
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "allow filters to modify the executing request" in {
      withClient() { client =>
        val appendedHeader = "X-Request-Id"
        val appendedHeaderValue = "someid"
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .get().map { response ⇒
            response.headers("X-Request-Id").head must be_==("someid")
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }

    "allow filters to modify the streaming request" in {
      withClient() { client =>
        val appendedHeader = "X-Request-Id"
        val appendedHeaderValue = "someid"
        client.url(s"http://localhost:$testServerPort")
          .withRequestFilter(new HeaderAppendingFilter(appendedHeader, appendedHeaderValue))
          .withMethod("GET").stream().map { response ⇒
            response.headers("X-Request-Id").head must be_==("someid")
          }.await(retries = 0, timeout = defaultTimeout)
      }
    }
  }
}
