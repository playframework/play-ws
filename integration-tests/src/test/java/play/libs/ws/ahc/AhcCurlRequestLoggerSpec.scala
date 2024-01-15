/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.mvc.Results
import play.libs.ws.DefaultBodyWritables
import play.libs.ws.DefaultWSCookie
import play.libs.ws.WSAuthInfo
import play.libs.ws.WSAuthScheme
import uk.org.lidalia.slf4jext.Level
import uk.org.lidalia.slf4jtest.TestLogger
import uk.org.lidalia.slf4jtest.TestLoggerFactory

import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import play.api.routing.sird._

class AhcCurlRequestLoggerSpec
    extends AnyWordSpec
    with NettyServerProvider
    with StandaloneWSClientSupport
    with ScalaFutures
    with DefaultBodyWritables {

  override def routes(components: BuiltInComponents) = {
    case GET(_) =>
      components.defaultActionBuilder(
        Results.Ok("<h1>Say hello to play</h1>")
      )
    case POST(_) =>
      components.defaultActionBuilder { req =>
        Results.Ok(
          req.body.asText.getOrElse("")
        )
      }
  }

  // Level.OFF because we don't want to pollute the test output
  def createTestLogger: TestLogger = new TestLoggerFactory(Level.OFF).getLogger("test-logger")

  "Logging request as curl" should {

    "be verbose" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("--verbose")))
    }

    "add all headers" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addHeader("My-Header", "My-Header-Value")
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      assert(messages.exists(_.contains("--header 'My-Header: My-Header-Value'")))
    }

    "add all cookies" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addCookie(new DefaultWSCookie("cookie1", "value1", "localhost", "path", 10L, true, true))
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      assert(messages.exists(_.contains("""--cookie 'cookie1=value1'""")))
    }

    "add method" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      assert(
        testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("--request GET"))
      )
    }

    "add authorization header" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setAuth(new WSAuthInfo("username", "password", WSAuthScheme.BASIC))
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      assert(
        testLogger.getLoggingEvents.asScala
          .map(_.getMessage)
          .exists(
            _.contains(
              """--header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ='"""
            )
          )
      )
    }

    "handle body" should {

      "add when in memory" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .setBody(body("the-body"))
          .setRequestFilter(curlRequestLogger)
          .get()
          .asScala
          .futureValue

        assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("the-body")))
      }

      "do nothing for empty bodies" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

        // no body setBody, so body is "empty"
        client
          .url(s"http://localhost:$testServerPort/")
          .setRequestFilter(curlRequestLogger)
          .get()
          .asScala
          .futureValue

        assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).forall(!_.contains("--data")))
      }
    }

    "print complete curl command" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setBody(body("the-body"))
        .addHeader("My-Header", "My-Header-Value")
        .setAuth(new WSAuthInfo("username", "password", WSAuthScheme.BASIC))
        .setRequestFilter(curlRequestLogger)
        .get()
        .asScala
        .futureValue

      assert(
        testLogger.getLoggingEvents.get(0).getMessage ==
          s"""
             |curl \\
             |  --verbose \\
             |  --request GET \\
             |  --header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=' \\
             |  --header 'content-type: text/plain' \\
             |  --header 'My-Header: My-Header-Value' \\
             |  --data 'the-body' \\
             |  'http://localhost:$testServerPort/'
        """.stripMargin.trim
      )
    }
  }
}
