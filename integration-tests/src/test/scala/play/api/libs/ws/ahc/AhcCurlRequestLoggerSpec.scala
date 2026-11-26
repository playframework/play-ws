/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.specs2.concurrent.ExecutionEnv
import org.specs2.concurrent.FutureAwait
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.DefaultBodyReadables
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSSignatureCalculator
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import uk.org.lidalia.slf4jext.Level
import uk.org.lidalia.slf4jtest.TestLogger
import uk.org.lidalia.slf4jtest.TestLoggerFactory
import play.api.routing.sird._
import play.shaded.ahc.org.asynchttpclient.Request
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator

import scala.concurrent.Await
import scala.jdk.CollectionConverters._

class AhcCurlRequestLoggerSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with FutureAwait
    with DefaultBodyReadables
    with DefaultBodyWritables {

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = {
    case GET(p"/auth") =>
      components.defaultActionBuilder { req =>
        Results.Ok(req.headers.get("Authorization").getOrElse(""))
      }
    case POST(p"/signed") =>
      components.defaultActionBuilder { req =>
        Results.Ok(req.headers.get("X-Signed").getOrElse(""))
      }
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

    "quote cookies" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .addCookies(DefaultWSCookie("cookie1", "it's"))
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage).mkString must contain("""--cookie 'cookie1=it'\''s'""")
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

    "add the authorization header that the request sends" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      // Not ASCII, and its Base64 has a '/' and a '+'.
      val request = client
        .url(s"http://localhost:$testServerPort/auth")
        .withAuth("username", "p\u00e4ss?w\u00f6rd>", WSAuthScheme.BASIC)
        .withRequestFilter(curlRequestLogger)
      val received = Await.result(request.get(), defaultTimeout).body[String]

      received must beEqualTo("Basic dXNlcm5hbWU6cMOkc3M/d8O2cmQ+")
      testLogger.getLoggingEvents.asScala.map(_.getMessage).mkString must contain(
        s"--header 'Authorization: $received'"
      )
    }

    "add no authorization header for Digest" in withClient() { client =>
      val testLogger        = createTestLogger
      val curlRequestLogger = AhcCurlRequestLogger(testLogger)

      client
        .url(s"http://localhost:$testServerPort/")
        .withAuth("username", "password", WSAuthScheme.DIGEST)
        .withRequestFilter(curlRequestLogger)
        .get()
        .awaitFor(defaultTimeout)

      testLogger.getLoggingEvents.asScala.map(_.getMessage) must not containMatch "Authorization"
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

      "sign an in-memory body" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)
        val signer            = new WSSignatureCalculator with SignatureCalculator {
          override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[?]): Unit = {
            if (request.getByteData == null && request.getStringData == null) {
              throw new IllegalStateException("Signer received no body")
            }
            requestBuilder.addHeader("X-Signed", "yes")
          }
        }

        val request = client
          .url(s"http://localhost:$testServerPort/signed")
          .sign(signer)
          .withRequestFilter(curlRequestLogger)
        val response = Await.result(request.post("the-body"), defaultTimeout)

        response.body[String] must beEqualTo("yes")
        testLogger.getLoggingEvents.asScala.map(_.getMessage) must containMatch("--data 'the-body'")
      }

      "sign a streamed body only for the request" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)
        val signings          = new AtomicInteger()
        val signer            = new WSSignatureCalculator with SignatureCalculator {
          override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[?]): Unit = {
            signings.incrementAndGet()
            requestBuilder.addHeader("X-Signed", "yes")
          }
        }

        val request = client
          .url(s"http://localhost:$testServerPort/signed")
          .sign(signer)
          .withRequestFilter(curlRequestLogger)
        val response = Await.result(request.post(Source.single(ByteString("the-body"))), defaultTimeout)

        response.body[String] must beEqualTo("yes")
        signings.get must beEqualTo(1)
      }

      "leave a streamed body to the request" in withClient() { client =>
        val testLogger        = createTestLogger
        val curlRequestLogger = AhcCurlRequestLogger(testLogger)
        val materializations  = new AtomicInteger()
        val body = Source.single(ByteString("the-body")).mapMaterializedValue(_ => materializations.incrementAndGet())

        val request = client
          .url(s"http://localhost:$testServerPort/")
          .addHttpHeaders("Content-Type" -> "text/plain", "Content-Length" -> "8")
          .withRequestFilter(curlRequestLogger)
        val response = Await.result(request.post(body), defaultTimeout)

        response.body[String] must beEqualTo("the-body")
        materializations.get must beEqualTo(1)
        val message = testLogger.getLoggingEvents.get(0).getMessage
        message must not contain "--data"
        // Without the body, a curl command with the length would leave the server waiting for it.
        message must not contain "Content-Length"
        message must endWith("\n# The streamed request body is not shown.")
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
