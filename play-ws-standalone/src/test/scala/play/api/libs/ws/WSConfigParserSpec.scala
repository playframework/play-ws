/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class WSConfigParserSpec extends AnyWordSpec {

  "WSConfigParser" should {

    def parseThis(input: String) = {
      val config = ConfigFactory.parseString(input).withFallback(ConfigFactory.defaultReference())
      val parser = new WSConfigParser(config, this.getClass.getClassLoader)
      parser.parse()
    }

    "parse ws base section" in {
      val actual = parseThis("""
                               |play.ws.timeout.connection = 9999 ms
                               |play.ws.timeout.idle = 666 ms
                               |play.ws.timeout.request = 1234 ms
                               |play.ws.followRedirects = false
                               |play.ws.useProxyProperties = false
                               |play.ws.useragent = "FakeUserAgent"
                             """.stripMargin)

      assert(actual.connectionTimeout == 9999.millis)
      assert(actual.idleTimeout == 666.millis)
      assert(actual.requestTimeout == 1234.millis)

      // default: true
      assert(actual.followRedirects == false)

      // default: true
      assert(actual.useProxyProperties == false)

      assert(actual.userAgent == Some("FakeUserAgent"))
    }
  }
}
