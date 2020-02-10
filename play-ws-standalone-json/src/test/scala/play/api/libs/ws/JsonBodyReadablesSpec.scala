/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.matcher.MustMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue

class JsonBodyReadablesSpec extends Specification with MustMatchers {

  class StubResponse(byteArray: Array[Byte], charset: Charset = UTF_8) extends StandaloneWSResponse {
    override def uri: java.net.URI = ???

    override def headers: Map[String, Seq[String]] = ???

    override def underlying[T]: T = ???

    override def status: Int = ???

    override def statusText: String = ???

    override def cookies: Seq[WSCookie] = ???

    override def cookie(name: String): Option[WSCookie] = ???

    override def body: String = new String(byteArray, charset)

    override def bodyAsBytes: ByteString = ByteString.fromArray(byteArray)

    override def bodyAsSource: Source[ByteString, _] = ???
  }

  "decode encodings correctly" should {

    "read an encoding of UTF-32BE" in {
      val readables   = new JsonBodyReadables() {}
      val json        = """{"menu": {"id": "file", "value": "File"} }"""
      val charsetName = "UTF-32BE"
      val value: JsValue =
        readables.readableAsJson.transform(new StubResponse(json.getBytes(charsetName), Charset.forName(charsetName)))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-32LE" in {
      val readables   = new JsonBodyReadables() {}
      val json        = """{"menu": {"id": "file", "value": "File"} }"""
      val charsetName = "UTF-32LE"
      val value: JsValue =
        readables.readableAsJson.transform(new StubResponse(json.getBytes(charsetName), Charset.forName(charsetName)))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-16BE" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val charset        = UTF_16BE
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(charset), charset))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-16LE" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val charset        = UTF_16LE
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(charset), charset))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-8" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      (value \ "menu" \ "id").validate[String] must beEqualTo(JsSuccess("file"))
    }

    "read an encoding of UTF-8 with empty object" in {
      val readables      = new JsonBodyReadables() {}
      val json           = "{}"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      value.toString() must beEqualTo("{}")
    }

    "read an encoding of UTF-8 with empty array" in {
      val readables      = new JsonBodyReadables() {}
      val json           = "[]"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      value.toString() must beEqualTo("[]")
    }

  }

}
