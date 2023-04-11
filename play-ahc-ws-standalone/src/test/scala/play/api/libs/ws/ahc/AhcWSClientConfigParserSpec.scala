/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

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
      s1.maxNumberOfRedirects must_== s2.maxNumberOfRedirects
      s1.maxRequestRetry must_== s2.maxRequestRetry
      s1.disableUrlEncoding must_== s2.disableUrlEncoding
      s1.keepAlive must_== s2.keepAlive
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

      actual.maxConnectionsPerHost must_== 3
      actual.maxConnectionsTotal must_== 6
      actual.maxConnectionLifetime must_== 1.minute
      actual.idleConnectionInPoolTimeout must_== 30.seconds
      actual.connectionPoolCleanerPeriod must_== 10.seconds
      actual.maxNumberOfRedirects must_== 0
      actual.maxRequestRetry must_== 99
      actual.disableUrlEncoding must beTrue
      actual.keepAlive must beFalse
    }

    "with keepAlive" should {
      "parse keepAlive default as true" in {
        val actual = parseThis("""""".stripMargin)

        actual.keepAlive must beTrue
      }
    }

  }
}
