/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.util.ByteString
import org.specs2.matcher.MustMatchers
import org.specs2.mutable.Specification

class JsonBodyReadablesSpec extends Specification with MustMatchers {

  "decode" should {

    "read an encoding of UTF-32BE" in {
      // 00 00 00 xx - it's UTF-32BE
      val readables = new JsonBodyReadables() {}
      val encoding = readables.detectEncoding(ByteString.fromArray(Array[Byte](0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x20)))
      encoding must beEqualTo("UTF-32BE")
    }

    "read an encoding of UTF-32LE" in {
      // xx 00 00 00 - it's UTF-32LE
      val readables = new JsonBodyReadables() {}
      val encoding = readables.detectEncoding(ByteString.fromArray(Array[Byte](0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20)))
      encoding must beEqualTo("UTF-32LE")
    }

    "read an encoding of UTF-16BE" in {
      // 00 xx 00 xx - it's UTF-16BE
      val readables = new JsonBodyReadables() {}
      val encoding = readables.detectEncoding(ByteString.fromArray(Array[Byte](0x00, 0x20, 0x00, 0x20, 0x00, 0x00, 0x00, 0x20)))
      encoding must beEqualTo("UTF-16BE")
    }

    "read an encoding of UTF-16LE" in {
      // xx 00 xx 00 - it's UTF-16LE
      val readables = new JsonBodyReadables() {}
      val encoding = readables.detectEncoding(ByteString.fromArray(Array[Byte](0x00, 0x20, 0x00, 0x20, 0x00, 0x00, 0x00, 0x20)))
      encoding must beEqualTo("UTF-16BE")
    }

    "read an encoding of UTF-8" in {
      // xx xx xx xx - it's UTF-8
      val readables = new JsonBodyReadables() {}
      val encoding = readables.detectEncoding(ByteString.fromArray(Array[Byte](0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x20)))
      encoding must beEqualTo("UTF-8")
    }
  }

}
