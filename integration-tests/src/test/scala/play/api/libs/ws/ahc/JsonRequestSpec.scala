/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.json.{ JsString, Json }
import play.api.libs.ws.JsonBodyWritables
import play.libs.ws.DefaultObjectMapper

/**
 *
 */
class JsonRequestSpec extends Specification with Mockito with AfterAll with JsonBodyWritables {
  sequential

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

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
}
