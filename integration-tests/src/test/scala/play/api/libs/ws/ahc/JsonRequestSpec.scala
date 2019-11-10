/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import org.mockito.Mockito.{ times, verify, when }
import org.specs2.mock.Mockito
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.json.{ JsString, JsValue, Json }
import play.api.libs.ws.{ JsonBodyReadables, JsonBodyWritables, StandaloneWSResponse }
import play.libs.ws.DefaultObjectMapper
import play.shaded.ahc.org.asynchttpclient.Response

import scala.io.Codec

/**
 *
 */
class JsonRequestSpec extends Specification with Mockito with AfterAll with JsonBodyWritables {
  sequential

  implicit val system = ActorSystem()
  implicit val materializer = Materializer.matFromSystem

  override def afterAll: Unit = {
    system.terminate()
  }

  "set a json node" in {
    val jsValue = Json.obj("k1" -> JsString("v1"))
    val client = mock[StandaloneAhcWSClient]
    val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(jsValue)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    req.getHeaders.get("Content-Type") must be_==("application/json")
    ByteString.fromArray(req.getByteData).utf8String must be_==("""{"k1":"v1"}""")
  }

  "set a json node using the default object mapper" in {
    val objectMapper = DefaultObjectMapper.instance

    implicit val jsonReadable = body(objectMapper)
    val jsonNode = objectMapper.readTree("""{"k1":"v1"}""")
    val client = mock[StandaloneAhcWSClient]
    val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(jsonNode)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    req.getHeaders.get("Content-Type") must be_==("application/json")
    ByteString.fromArray(req.getByteData).utf8String must be_==("""{"k1":"v1"}""")
  }

  "read an encoding of UTF-8" in {
    val json = io.Source.fromResource("test.json")(Codec.ISO8859).getLines.mkString

    val ahcResponse = mock[Response]
    val response = new StandaloneAhcWSResponse(ahcResponse)

    when(ahcResponse.getResponseBody(StandardCharsets.UTF_8)).thenReturn(json)
    when(ahcResponse.getContentType).thenReturn("application/json")

    val value: JsValue = JsonBodyReadables.readableAsJson.transform(response)
    verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.UTF_8)
    verify(ahcResponse, times(1)).getContentType
    value.toString must beEqualTo(json)
  }

  "read an encoding of ISO-8859-1" in {
    val json = io.Source.fromResource("test.json")(Codec.ISO8859).getLines.mkString

    val ahcResponse = mock[Response]
    val response = new StandaloneAhcWSResponse(ahcResponse)

    when(ahcResponse.getResponseBody(StandardCharsets.ISO_8859_1)).thenReturn(json)
    when(ahcResponse.getContentType).thenReturn("application/json;charset=iso-8859-1")

    val value: JsValue = JsonBodyReadables.readableAsJson.transform(response)
    verify(ahcResponse, times(1)).getResponseBody(StandardCharsets.ISO_8859_1)
    verify(ahcResponse, times(1)).getContentType
    value.toString must beEqualTo(json)
  }
}
