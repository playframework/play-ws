/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.concurrent.FutureAwait
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.libs.ws.DefaultBodyWritables
import play.libs.ws.DefaultWSCookie
import play.libs.ws.WSAuthInfo
import play.libs.ws.WSAuthScheme
import uk.org.lidalia.slf4jext.Level
import uk.org.lidalia.slf4jtest.TestLogger
import uk.org.lidalia.slf4jtest.TestLoggerFactory

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class AhcCurlRequestLoggerSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with AkkaServerProvider
    with StandaloneWSClientSupport
    with FutureAwait
    with DefaultBodyWritables {

  override def routes: Route = {
    import akka.http.scaladsl.server.Directives._
    get {
      complete("<h1>Say hello to akka-http</h1>")
    } ~
      post {
        entity(as[String]) { echo =>
          complete(echo)
        }
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
        .toScala
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("--verbose")
    }

    "add all headers" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addHeader("My-Header", "My-Header-Value")
        .setRequestFilter(curlRequestLogger)
        .get()
        .toScala
        .awaitFor(defaultTimeout)

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      messages must containMatch("--header 'My-Header: My-Header-Value'")
    }

    "add all cookies" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addCookie(new DefaultWSCookie("cookie1", "value1", "localhost", "path", 10L, true, true))
        .setRequestFilter(curlRequestLogger)
        .get()
        .toScala
        .awaitFor(defaultTimeout)

      val messages = testLogger.getLoggingEvents.asScala.map(_.getMessage)

      messages must containMatch("""--cookie 'cookie1=value1'""")
    }

    "add method" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setRequestFilter(curlRequestLogger)
        .get()
        .toScala
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("--request GET")
    }

    "add authorization header" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .setAuth(new WSAuthInfo("username", "password", WSAuthScheme.BASIC))
        .setRequestFilter(curlRequestLogger)
        .get()
        .toScala
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch(
        """--header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ='"""
      )
    }

    "handle body" in {

      "add when in memory" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

        client
          .url(s"http://localhost:$testServerPort/")
          .setBody(body("the-body"))
          .setRequestFilter(curlRequestLogger)
          .get()
          .toScala
          .awaitFor(defaultTimeout)

        testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("the-body")
      }

      "do nothing for empty bodies" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = new AhcCurlRequestLogger(testLogger)

        // no body setBody, so body is "empty"
        client
          .url(s"http://localhost:$testServerPort/")
          .setRequestFilter(curlRequestLogger)
          .get()
          .toScala
          .awaitFor(defaultTimeout)

        testLogger.getLoggingEvents.asScala.map(_.getMessage) must not containMatch "--data"
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
        .toScala
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.get(0).getMessage must beEqualTo(s"""
                                                                      |curl \\
                                                                      |  --verbose \\
                                                                      |  --request GET \\
                                                                      |  --header 'Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=' \\
                                                                      |  --header 'content-type: text/plain' \\
                                                                      |  --header 'My-Header: My-Header-Value' \\
                                                                      |  --data 'the-body' \\
                                                                      |  'http://localhost:$testServerPort/'
        """.stripMargin.trim)
    }
  }
}
