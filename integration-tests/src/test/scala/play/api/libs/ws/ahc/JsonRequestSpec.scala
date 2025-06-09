/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.Mockito

import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyReadables
import play.api.libs.ws.JsonBodyWritables
import play.libs.ws.DefaultObjectMapper
import play.shaded.ahc.org.asynchttpclient.Response

import scala.io.Codec
import scala.reflect.ClassTag

/**
 */
class JsonRequestSpec extends Specification with AfterAll with JsonBodyWritables {
  sequential

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  implicit val system: ActorSystem        = ActorSystem()
  implicit val materializer: Materializer = Materializer.matFromSystem

  override def afterAll(): Unit = {
    system.terminate()
  }

  "set a json node" in {
    val jsValue = Json.obj("k1" -> JsString("v1"))
    val client  = StandaloneAhcWSClient()
    val req     = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(jsValue)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    req.getHeaders.get("Content-Type") must be_==("application/json")
    ByteString.fromArray(req.getByteData).utf8String must be_==("""{"k1":"v1"}""")
  }

  "set a json node using the default object mapper" in {
    val objectMapper = DefaultObjectMapper.instance

    implicit val jsonReadable = body(objectMapper)
    val jsonNode              = objectMapper.readTree("""{"k1":"v1"}""")
    val client                = StandaloneAhcWSClient()
    val req                   = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(jsonNode)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    req.getHeaders.get("Content-Type") must be_==("application/json")
    ByteString.fromArray(req.getByteData).utf8String must be_==("""{"k1":"v1"}""")
  }

  "read an encoding of UTF-8" in {
    val json = scala.io.Source.fromResource("test.json")(Codec.ISO8859).getLines().mkString

    val ahcResponse = mock[Response]
    val response    = new StandaloneAhcWSResponse(ahcResponse)

    when(ahcResponse.getResponseBody(StandardCharsets.UTF_8)).thenReturn(json)
    when(ahcResponse.getContentType).thenReturn("application/json")

    val value: JsValue = JsonBodyReadables.readableAsJson.transform(response)
    verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.UTF_8)
    verify(ahcResponse, times(1)).getContentType
    value.toString must beEqualTo(json)
  }

  "read an encoding of ISO-8859-1" in {
    val json = scala.io.Source.fromResource("test.json")(Codec.ISO8859).getLines().mkString

    val ahcResponse = mock[Response]
    val response    = new StandaloneAhcWSResponse(ahcResponse)

    when(ahcResponse.getResponseBody(StandardCharsets.ISO_8859_1)).thenReturn(json)
    when(ahcResponse.getContentType).thenReturn("application/json;charset=iso-8859-1")

    val value: JsValue = JsonBodyReadables.readableAsJson.transform(response)
    verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.ISO_8859_1)
    verify(ahcResponse, times(1)).getContentType
    value.toString must beEqualTo(json)
  }
}
