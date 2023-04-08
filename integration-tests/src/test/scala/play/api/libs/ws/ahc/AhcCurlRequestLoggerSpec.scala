/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.concurrent.FutureAwait
import org.specs2.mutable.Specification
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

class AhcCurlRequestLoggerSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with FutureAwait
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
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("--verbose")
    }

    "add all headers" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addHttpHeaders("My-Header" -> "My-Header-Value")
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      messages must containMatch("--header 'My-Header: My-Header-Value'")
    }

    "add all cookies" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addCookies(DefaultWSCookie("cookie1", "value1"))
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      messages must containMatch("--cookie 'cookie1=value1'")
    }

    "add method" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("--request GET")
    }

    "add authorization header" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withAuth("username", "password", WSAuthScheme.BASIC)
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch(
        """--header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ='"""
      )
    }

    "handle body" in {

      "add when in memory" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .withBody("the-body")
          .withRequestFilter(curlRequestLogger)
          .get()
          .awaitFor(defaultTimeout)

        testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("the-body")
      }

      "do nothing for empty bodies" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .withBody(EmptyBody)
          .withRequestFilter(curlRequestLogger)
          .get()
          .awaitFor(defaultTimeout)

        testLogger.getLoggingEvents.asScala.map(_.getMessage) must not containMatch "--data"
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
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.get(0).getMessage must beEqualTo(
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
