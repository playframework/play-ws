/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.ws.{ XML, XMLBodyReadables, XMLBodyWritables }
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }

import scala.xml.Elem

/**
 *
 */
class XMLRequestSpec extends Specification with Mockito with AfterAll {
  sequential

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll: Unit = {
    system.terminate()
  }

  "write an XML node" in {
    import XMLBodyWritables._

    val xml = XML.parser.loadString("<hello><test></test></hello>")
    val client = mock[StandaloneAhcWSClient]
    val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(xml)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    req.getHeaders.get("Content-Type") must be_==("text/xml; charset=UTF-8")
    ByteString.fromArray(req.getByteData).utf8String must be_==("<hello><test/></hello>")
  }

  "read an XML node" in {
    import XMLBodyReadables._

    val ahcResponse = mock[AHCResponse]
    ahcResponse.getContentType() returns "application/xml"
    ahcResponse.getResponseBody(StandardCharsets.UTF_8) returns "<hello><test></test></hello>"
    val response = new StandaloneAhcWSResponse(ahcResponse)

    val expected = XML.parser.loadString("<hello><test></test></hello>")
    val actual = response.body[Elem]
    actual must be_==(expected)
  }
}
