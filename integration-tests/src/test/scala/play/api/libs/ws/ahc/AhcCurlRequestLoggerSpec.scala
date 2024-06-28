/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.WSAuthScheme
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import uk.org.lidalia.slf4jext.Level
import uk.org.lidalia.slf4jtest.TestLogger
import uk.org.lidalia.slf4jtest.TestLoggerFactory
import play.api.routing.sird._

import scala.jdk.CollectionConverters._

class AhcCurlRequestLoggerSpec
    extends AnyWordSpec
    with NettyServerProvider
    with StandaloneWSClientSupport
    with ScalaFutures
    with DefaultBodyWritables {

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = {
    case GET(_) =>
      components.defaultActionBuilder(
        Results
          .Ok("<h1>Say hello to play</h1>")
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
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withRequestFilter(curlRequestLogger)
        .get()
        .futureValue

      assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("--verbose")))
    }

    "add all headers" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addHttpHeaders("My-Header" -> "My-Header-Value")
        .withRequestFilter(curlRequestLogger)
        .get()
        .futureValue

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      assert(messages.exists(_.contains("--header 'My-Header: My-Header-Value'")))
    }

    "add all cookies" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addCookies(DefaultWSCookie("cookie1", "value1"))
        .withRequestFilter(curlRequestLogger)
        .get()
        .futureValue

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      assert(messages.exists(_.contains("--cookie 'cookie1=value1'")))
    }

    "add method" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withRequestFilter(curlRequestLogger)
        .get()
        .futureValue

      assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("--request GET")))
    }

    "add authorization header" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withAuth("username", "password", WSAuthScheme.BASIC)
        .withRequestFilter(curlRequestLogger)
        .get()
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
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .withBody("the-body")
          .withRequestFilter(curlRequestLogger)
          .get()
          .futureValue

        assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).exists(_.contains("the-body")))
      }

      "do nothing for empty bodies" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .withBody(EmptyBody)
          .withRequestFilter(curlRequestLogger)
          .get()
          .futureValue

        assert(testLogger.getLoggingEvents.asScala.map(_.getMessage).forall(!_.contains("--data")))
      }
    }

    "print complete curl command" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withBody("the-body")
        .addHttpHeaders("My-Header" -> "My-Header-Value")
        .withAuth("username", "password", WSAuthScheme.BASIC)
        .withRequestFilter(curlRequestLogger)
        .get()
        .futureValue

      assert(
        testLogger.getLoggingEvents.get(0).getMessage ==
          s"""
             |curl \\
             |  --verbose \\
             |  --request GET \\
             |  --header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=' \\
             |  --header 'My-Header: My-Header-Value' \\
             |  --header 'Content-Type: text/plain' \\
             |  --data 'the-body' \\
             |  'http://localhost:$testServerPort/'
        """.stripMargin.trim
      )
    }
  }
}
