/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.ws._

import scala.xml.Elem

/**
 */
class XMLRequestSpec extends AnyWordSpec with BeforeAndAfterAll {

  implicit val system: ActorSystem        = ActorSystem()
  implicit val materializer: Materializer = Materializer.matFromSystem

  override def afterAll(): Unit = {
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

    override def bodyAsSource: org.apache.pekko.stream.scaladsl.Source[ByteString, _] = ???
  }

  "write an XML node" in {
    import XMLBodyWritables._

    val xml    = XML.parser.loadString("<hello><test></test></hello>")
    val client = StandaloneAhcWSClient()
    val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      .withBody(xml)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    assert(req.getHeaders.get("Content-Type") == "text/xml; charset=UTF-8")
    assert(ByteString.fromArray(req.getByteData).utf8String == "<hello><test/></hello>")
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
    assert((value \\ "note" \ "to").text == "Tove")
    assert((value \\ "note" \ "from").text == "Jani")
    assert((value \\ "note" \ "heading").text == "Reminder")
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
    assert((value \\ "note" \ "to").text == "Tove")
    assert((value \\ "note" \ "from").text == "Jani")
    assert((value \\ "note" \ "heading").text == "Reminder")
  }
}
