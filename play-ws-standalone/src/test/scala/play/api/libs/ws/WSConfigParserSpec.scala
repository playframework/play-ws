/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import org.specs2.mutable._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class WSConfigParserSpec extends Specification {

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

      actual.connectionTimeout must_== 9999.millis
      actual.idleTimeout must_== 666.millis
      actual.requestTimeout must_== 1234.millis

      // default: true
      actual.followRedirects must beFalse

      // default: true
      actual.useProxyProperties must beFalse

      actual.userAgent must beSome.which(_ must_== "FakeUserAgent")
    }
  }
}
