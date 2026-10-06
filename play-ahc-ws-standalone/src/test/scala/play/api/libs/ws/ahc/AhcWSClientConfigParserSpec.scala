/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import org.specs2.mutable._
import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration._

class AhcWSClientConfigParserSpec extends Specification {

  val defaultWsConfig = WSClientConfig()
  val defaultConfig   = AhcWSClientConfig(defaultWsConfig)

  "AhcWSClientConfigParser" should {

    def parseThis(input: String) = {
      val classLoader = this.getClass.getClassLoader
      val config      = ConfigFactory.parseString(input).withFallback(ConfigFactory.defaultReference())
      val parser      = new AhcWSClientConfigParser(defaultWsConfig, config, classLoader)
      parser.parse()
    }

    "case class defaults must match reference.conf defaults" in {
      val s1 = parseThis("")
      val s2 = AhcWSClientConfig()

      // since we use typesafe ssl-config we can't match the objects directly since they aren't case classes,
      // and also AhcWSClientConfig has a duration which will be parsed into nanocseconds while the case class uses minutes
      s1.wsClientConfig.toString must_== s2.wsClientConfig.toString
      s1.maxConnectionsPerHost must_== s2.maxConnectionsPerHost
      s1.maxConnectionsTotal must_== s2.maxConnectionsTotal
      s1.maxConnectionLifetime must_== s2.maxConnectionLifetime
      s1.idleConnectionInPoolTimeout must_== s2.idleConnectionInPoolTimeout
      s1.connectionPoolCleanerPeriod must_== s2.connectionPoolCleanerPeriod
      s1.maxNumberOfRedirects must_== s2.maxNumberOfRedirects
      s1.maxRequestRetry must_== s2.maxRequestRetry
      s1.disableUrlEncoding must_== s2.disableUrlEncoding
      s1.keepAlive must_== s2.keepAlive
      s1.useLaxCookieEncoder must_== s2.useLaxCookieEncoder
      s1.useCookieStore must_== s2.useCookieStore
      s1.http2Enabled must_== s2.http2Enabled
      s1.http2InitialWindowSize must_== s2.http2InitialWindowSize
      s1.http2MaxConcurrentStreams must_== s2.http2MaxConcurrentStreams
      s1.maxDecompressedResponseSize must_== s2.maxDecompressedResponseSize
      s1.shutdownQuietPeriod must_== s2.shutdownQuietPeriod
      s1.shutdownTimeout must_== s2.shutdownTimeout
      s1.refuseSchemeDowngradeOnRedirect must_== s2.refuseSchemeDowngradeOnRedirect
      s1.refuseCrossOriginBodyOnRedirect must_== s2.refuseCrossOriginBodyOnRedirect
    }

    "parse ws ahc section" in {
      val actual = parseThis("""
                               |play.ws.ahc.maxConnectionsPerHost = 3
                               |play.ws.ahc.maxConnectionsTotal = 6
                               |play.ws.ahc.maxConnectionLifetime = 1 minute
                               |play.ws.ahc.idleConnectionInPoolTimeout = 30 seconds
                               |play.ws.ahc.connectionPoolCleanerPeriod = 10 seconds
                               |play.ws.ahc.maxNumberOfRedirects = 0
                               |play.ws.ahc.maxRequestRetry = 99
                               |play.ws.ahc.disableUrlEncoding = true
                               |play.ws.ahc.keepAlive = false
                               |play.ws.ahc.useLaxCookieEncoder = true
                               |play.ws.ahc.useCookieStore = true
                               |play.ws.ahc.http2Enabled = true
                               |play.ws.ahc.http2InitialWindowSize = 64 KiB
                               |play.ws.ahc.http2MaxConcurrentStreams = 32
                               |play.ws.ahc.maxDecompressedResponseSize = 10 MiB
                               |play.ws.ahc.shutdownQuietPeriod = 25 milliseconds
                               |play.ws.ahc.shutdownTimeout = 3 seconds
                               |play.ws.ahc.refuseSchemeDowngradeOnRedirect = true
                               |play.ws.ahc.refuseCrossOriginBodyOnRedirect = false
        """.stripMargin)

      actual.maxConnectionsPerHost must_== 3
      actual.maxConnectionsTotal must_== 6
      actual.maxConnectionLifetime must_== 1.minute
      actual.idleConnectionInPoolTimeout must_== 30.seconds
      actual.connectionPoolCleanerPeriod must_== 10.seconds
      actual.maxNumberOfRedirects must_== 0
      actual.maxRequestRetry must_== 99
      actual.disableUrlEncoding must beTrue
      actual.keepAlive must beFalse
      actual.useLaxCookieEncoder must beTrue
      actual.useCookieStore must beTrue
      actual.http2Enabled must beTrue
      actual.http2InitialWindowSize must beSome(64 * 1024)
      actual.http2MaxConcurrentStreams must beSome(32)
      actual.maxDecompressedResponseSize must beSome(10L * 1024 * 1024)
      actual.shutdownQuietPeriod must_== 25.millis
      actual.shutdownTimeout must_== 3.seconds
      actual.refuseSchemeDowngradeOnRedirect must beSome(true)
      actual.refuseCrossOriginBodyOnRedirect must beSome(false)
    }

    "reject an infinite shutdown duration" in {
      parseThis("play.ws.ahc.shutdownTimeout = Inf") must throwA[ConfigException.BadValue]
    }

    "parse a bare zero shutdown duration" in {
      val actual = parseThis("""
                               |play.ws.ahc.shutdownQuietPeriod = 0
                               |play.ws.ahc.shutdownTimeout = 0
        """.stripMargin)

      actual.shutdownQuietPeriod must_== Duration.Zero
      actual.shutdownTimeout must_== Duration.Zero
    }

    "reject invalid shutdown timing" in {
      (parseThis("play.ws.ahc.shutdownQuietPeriod = -1 second") must throwA[ConfigException.BadValue])
        .and(
          parseThis("play.ws.ahc.shutdownTimeout = -1 second") must throwA[ConfigException.BadValue]
        )
        .and(
          parseThis("play.ws.ahc.shutdownQuietPeriod = 1 second") must throwA[ConfigException.BadValue]
        )
    }

    "accept the AHC sentinel for HTTP/2 max concurrent streams" in {
      parseThis("play.ws.ahc.http2MaxConcurrentStreams = -1").http2MaxConcurrentStreams must beSome(-1)
    }

    "reject invalid HTTP/2 limits" in {
      (parseThis("play.ws.ahc.http2InitialWindowSize = 0") must throwA[ConfigException.BadValue])
        .and(
          parseThis("play.ws.ahc.http2InitialWindowSize = 3 GiB") must throwA[ConfigException.BadValue]
        )
        .and(
          parseThis("play.ws.ahc.http2MaxConcurrentStreams = 0") must throwA[ConfigException.BadValue]
        )
        .and(
          parseThis("play.ws.ahc.http2MaxConcurrentStreams = -2") must throwA[ConfigException.BadValue]
        )
    }

    "identify a negative decompressed response limit as a bad configuration value" in {
      val key   = "play.ws.ahc.maxDecompressedResponseSize"
      val error = try {
        parseThis(s"$key = -1")
        throw new AssertionError("Expected the negative response limit to be rejected")
      } catch {
        case expected: ConfigException.BadValue => expected
      }

      error.getMessage must contain(key)
    }

    "with keepAlive" should {
      "parse keepAlive default as true" in {
        val actual = parseThis("""""".stripMargin)

        actual.keepAlive must beTrue
      }
    }

  }
}
