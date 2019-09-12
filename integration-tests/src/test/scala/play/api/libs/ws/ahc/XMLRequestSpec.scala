/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import org.specs2.matcher.MustMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.ws._

import scala.xml.Elem

/**
 *
 */
class XMLRequestSpec extends Specification with Mockito with AfterAll with MustMatchers {
  sequential

  implicit val system = ActorSystem()
  implicit val materializer = Materializer.matFromSystem

  override def afterAll: Unit = {
    system.terminate()
  }
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

    override def bodyAsSource: akka.stream.scaladsl.Source[ByteString, _] = ???
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

  "read an XML node in Utf-8" in {
    val test =
      """
        |<note>
        |<to>Tove</to>
        |<from>Jani</from>
        |<heading>Reminder</heading>
        |<body>Don't forget me this weekend!</body>
        |</note>
      """.stripMargin
    val readables = new XMLBodyReadables() {}
    /* UTF-8 */
    val value: Elem = readables.readableAsXml.transform(new StubResponse(test.getBytes(StandardCharsets.UTF_8)))
    (value \\ "note" \ "to").text must be_==("Tove")
    (value \\ "note" \ "from").text must be_==("Jani")
    (value \\ "note" \ "heading").text must be_==("Reminder")
  }

  "read an XML node in Utf-16" in {
    val test =
      """
        |<note>
        |<to>Tove</to>
        |<from>Jani</from>
        |<heading>Reminder</heading>
        |<body>Don't forget me this weekend!</body>
        |</note>
      """.stripMargin
    val readables = new XMLBodyReadables() {}
    /* UTF-16 */
    val value: Elem = readables.readableAsXml.transform(new StubResponse(test.getBytes(StandardCharsets.UTF_16)))
    (value \\ "note" \ "to").text must be_==("Tove")
    (value \\ "note" \ "from").text must be_==("Jani")
    (value \\ "note" \ "heading").text must be_==("Reminder")
  }
}
