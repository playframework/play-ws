/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue

class JsonBodyReadablesSpec extends AnyWordSpec {

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
      assert((value \ "menu" \ "id").validate[String] == JsSuccess("file"))
    }

    "read an encoding of UTF-32LE" in {
      val readables   = new JsonBodyReadables() {}
      val json        = """{"menu": {"id": "file", "value": "File"} }"""
      val charsetName = "UTF-32LE"
      val value: JsValue =
        readables.readableAsJson.transform(new StubResponse(json.getBytes(charsetName), Charset.forName(charsetName)))
      assert((value \ "menu" \ "id").validate[String] == JsSuccess("file"))
    }

    "read an encoding of UTF-16BE" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val charset        = UTF_16BE
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(charset), charset))
      assert((value \ "menu" \ "id").validate[String] == JsSuccess("file"))
    }

    "read an encoding of UTF-16LE" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val charset        = UTF_16LE
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(charset), charset))
      assert((value \ "menu" \ "id").validate[String] == JsSuccess("file"))
    }

    "read an encoding of UTF-8" in {
      val readables      = new JsonBodyReadables() {}
      val json           = """{"menu": {"id": "file", "value": "File"} }"""
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      assert((value \ "menu" \ "id").validate[String] == JsSuccess("file"))
    }

    "read an encoding of UTF-8 with empty object" in {
      val readables      = new JsonBodyReadables() {}
      val json           = "{}"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      assert(value.toString() == "{}")
    }

    "read an encoding of UTF-8 with empty array" in {
      val readables      = new JsonBodyReadables() {}
      val json           = "[]"
      val value: JsValue = readables.readableAsJson.transform(new StubResponse(json.getBytes(UTF_8)))
      assert(value.toString() == "[]")
    }

  }

}
