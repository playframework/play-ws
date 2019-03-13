/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.matcher.MustMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.{ JsSuccess, JsValue }

class JsonBodyReadablesSpec extends Specification with MustMatchers {

  class StubResponse(byteArray: Array[Byte]) extends StandaloneWSResponse {
    override def uri: java.net.URI = ???

    override def headers: Map[String, Seq[String]] = ???

    override def underlying[T]: T = ???

    override def status: Int = ???

    override def statusText: String = ???

    override def cookies: Seq[WSCookie] = ???

    override def cookie(name: String): Option[WSCookie] = ???

    override def body: String = ???

    override def bodyAsBytes: ByteString = ByteString.fromArray(byteArray)

    override def bodyAsSource: Source[ByteString, _] = ???
  }

  "decode encodings correctly" should {

    "read an encoding of UTF-32BE" in {
      val readables = new JsonBodyReadables() {}
      val json = """{"menu": {"id": "file", "value": "File"} }"""

      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-32BE")))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-32LE" in {
      val readables = new JsonBodyReadables() {}
      val json = """{"menu": {"id": "file", "value": "File"} }"""

      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-32LE")))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-16BE" in {
      val readables = new JsonBodyReadables() {}
      val json = """{"menu": {"id": "file", "value": "File"} }"""
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-16BE")))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-16LE" in {
      val readables = new JsonBodyReadables() {}
      val json = """{"menu": {"id": "file", "value": "File"} }"""
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-16LE")))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-8" in {
      val readables = new JsonBodyReadables() {}
      val json = """{"menu": {"id": "file", "value": "File"} }"""
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-8")))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-8 with empty object" in {
      val readables = new JsonBodyReadables() {}
      val json = "{}"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-8")))
      value.toString() must beEqualTo("{}")
    }

    "read an encoding of UTF-8 with empty array" in {
      val readables = new JsonBodyReadables() {}
      val json = "[]"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes("UTF-8")))
      value.toString() must beEqualTo("[]")
    }
  }

}
