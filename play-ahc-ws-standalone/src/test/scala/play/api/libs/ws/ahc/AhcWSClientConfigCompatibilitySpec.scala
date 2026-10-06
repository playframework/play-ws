/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import org.specs2.mutable.Specification
import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration._

class AhcWSClientConfigCompatibilitySpec extends Specification {

  private val wsClientConfig = WSClientConfig()

  "AhcWSClientConfig" should {
    "retain the Play WS 3.0 constructor" in {
      val actual = new AhcWSClientConfig(
        wsClientConfig,
        1,
        2,
        3.minutes,
        4.seconds,
        5.seconds,
        6,
        7,
        true,
        false,
        true,
        true
      )

      actual.maxConnectionsPerHost must_== 1
      actual.http2Enabled must beFalse
      actual.http2InitialWindowSize must beNone
      actual.maxDecompressedResponseSize must beNone
      actual.shutdownQuietPeriod must_== Duration.Zero
      actual.shutdownTimeout must_== Duration.Zero
      actual.refuseSchemeDowngradeOnRedirect must beNone
      actual.refuseCrossOriginBodyOnRedirect must beNone
    }

    "retain the Play WS 3.0 companion apply" in {
      val actual = AhcWSClientConfig(
        wsClientConfig,
        1,
        2,
        3.minutes,
        4.seconds,
        5.seconds,
        6,
        7,
        true,
        false,
        true,
        true
      )

      actual.maxConnectionsTotal must_== 2
      actual.useCookieStore must beTrue
    }

    "retain the Play WS 3.0 copy and preserve new settings" in {
      val original = AhcWSClientConfig(
        http2Enabled = true,
        http2InitialWindowSize = Some(65535),
        http2MaxConcurrentStreams = Some(16),
        maxDecompressedResponseSize = Some(1024L),
        shutdownQuietPeriod = 1.second,
        shutdownTimeout = 2.seconds,
        refuseSchemeDowngradeOnRedirect = Some(true),
        refuseCrossOriginBodyOnRedirect = Some(false)
      )
      val actual = original.copy(
        wsClientConfig,
        1,
        2,
        3.minutes,
        4.seconds,
        5.seconds,
        6,
        7,
        true,
        false,
        true,
        true
      )

      actual.maxConnectionsPerHost must_== 1
      actual.http2Enabled must beTrue
      actual.http2InitialWindowSize must beSome(65535)
      actual.http2MaxConcurrentStreams must beSome(16)
      actual.maxDecompressedResponseSize must beSome(1024L)
      actual.shutdownQuietPeriod must_== 1.second
      actual.shutdownTimeout must_== 2.seconds
      actual.refuseSchemeDowngradeOnRedirect must beSome(true)
      actual.refuseCrossOriginBodyOnRedirect must beSome(false)
    }

    "retain the Play WS 3.0 twelve-field extractor" in {
      val config = AhcWSClientConfig(
        wsClientConfig = wsClientConfig,
        maxConnectionsPerHost = 42,
        http2Enabled = false
      )

      val extracted = config match {
        case AhcWSClientConfig(
              extractedWsClientConfig,
              maxConnectionsPerHost,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          Some(extractedWsClientConfig -> maxConnectionsPerHost)
        case _ => None
      }

      extracted must beSome(wsClientConfig -> 42)
    }
  }
}
