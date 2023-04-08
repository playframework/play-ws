/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration._

class AhcWSClientConfigParserSpec extends AnyWordSpec {

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
      assert(s1.wsClientConfig.toString == s2.wsClientConfig.toString)
      assert(s1.maxConnectionsPerHost == s2.maxConnectionsPerHost)
      assert(s1.maxConnectionsTotal == s2.maxConnectionsTotal)
      assert(s1.maxConnectionLifetime == s2.maxConnectionLifetime)
      assert(s1.idleConnectionInPoolTimeout == s2.idleConnectionInPoolTimeout)
      assert(s1.maxNumberOfRedirects == s2.maxNumberOfRedirects)
      assert(s1.maxRequestRetry == s2.maxRequestRetry)
      assert(s1.disableUrlEncoding == s2.disableUrlEncoding)
      assert(s1.keepAlive == s2.keepAlive)
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
        """.stripMargin)

      assert(actual.maxConnectionsPerHost == 3)
      assert(actual.maxConnectionsTotal == 6)
      assert(actual.maxConnectionLifetime == 1.minute)
      assert(actual.idleConnectionInPoolTimeout == 30.seconds)
      assert(actual.connectionPoolCleanerPeriod == 10.seconds)
      assert(actual.maxNumberOfRedirects == 0)
      assert(actual.maxRequestRetry == 99)
      assert(actual.disableUrlEncoding)
      assert(actual.keepAlive == false)
    }

    "with keepAlive" should {
      "parse keepAlive default as true" in {
        val actual = parseThis("""""".stripMargin)

        assert(actual.keepAlive)
      }
    }

  }
}
