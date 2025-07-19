/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.mvc.{AnyContentAsText, AnyContentAsXml, Results}
import play.api.routing.sird._
import play.libs.ws._

import scala.concurrent.duration._
import scala.jdk.FutureConverters._

class AhcWSClientSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with FutureMatchers
    with XMLBodyWritables
    with XMLBodyReadables {

  override def routes(components: BuiltInComponents) = {
    case GET(_) =>
      components.defaultActionBuilder {
        Results.Ok(
          <h1>Say hello to play</h1>
        )
      }
    case POST(_) =>
      components.defaultActionBuilder { req =>
        req.body match {
          case AnyContentAsText(txt) =>
            Results.Ok(txt)
          case AnyContentAsXml(xml) =>
            Results.Ok(xml)
          case _ =>
            Results.NotFound
        }
      }
  }

  "play.libs.ws.ahc.StandaloneAhcWSClient" should {

    "get successfully" in withClient() { client =>
      def someOtherMethod(string: String) = {
        new InMemoryBodyWritable(org.apache.pekko.util.ByteString.fromString(string), "text/plain")
      }
      client
        .url(s"http://localhost:$testServerPort")
        .post(someOtherMethod("hello world"))
        .asScala
        .map(response => response.getBody() must be_==("hello world"))
        .await(retries = 0, timeout = 5.seconds)
    }

    "round trip XML successfully" in withClient() { client =>
      val document = XML.fromString("""<?xml version="1.0" encoding='UTF-8'?>
                                      |<note>
                                      |  <from>hello</from>
                                      |  <to>world</to>
                                      |</note>""".stripMargin)
      document.normalizeDocument()

      client
        .url(s"http://localhost:$testServerPort")
        .post(body(document))
        .asScala
        .map { response =>
          import javax.xml.parsers.DocumentBuilderFactory
          val dbf = DocumentBuilderFactory.newInstance
          dbf.setNamespaceAware(true)
          dbf.setCoalescing(true)
          dbf.setIgnoringElementContentWhitespace(true)
          dbf.setIgnoringComments(true)
          dbf.newDocumentBuilder

          val responseXml = response.getBody(xml())
          responseXml.normalizeDocument()

          (responseXml.isEqualNode(document) must beTrue).and {
            response.getUri must beEqualTo(new java.net.URI(s"http://localhost:$testServerPort"))
          }
        }
        .await(retries = 0, timeout = 5.seconds)
    }
  }
}
