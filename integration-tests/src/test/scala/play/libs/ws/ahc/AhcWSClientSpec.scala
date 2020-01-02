/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import akka.http.scaladsl.server.Route
import akka.stream.javadsl.Sink
import akka.util.ByteString
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider
import play.libs.ws._

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class AhcWSClientSpec(implicit val executionEnv: ExecutionEnv) extends Specification
  with AkkaServerProvider
  with StandaloneWSClientSupport
  with FutureMatchers
  with XMLBodyWritables
  with XMLBodyReadables {

  override val routes: Route = {
    import akka.http.scaladsl.server.Directives._
    get {
      complete("<h1>Say hello to akka-http</h1>")
    } ~
      post {
        entity(as[String]) { echo =>
          complete(echo)
        }
      }
  }

  "play.libs.ws.ahc.StandaloneAhcWSClient" should {

    "get successfully" in withClient() { client =>
      def someOtherMethod(string: String) = {
        new InMemoryBodyWritable(akka.util.ByteString.fromString(string), "text/plain")
      }
      toScala(client.url(s"http://localhost:$testServerPort").post(someOtherMethod("hello world"))).map(response =>
        response.getBody() must be_==("hello world")
      ).await(retries = 0, timeout = 5.seconds)
    }

    "source successfully" in withClient() { client =>
      val future = toScala(client.url(s"http://localhost:$testServerPort").stream())
      val result: Future[ByteString] = future.flatMap { response: StandaloneWSResponse =>
        toScala(response.getBodyAsSource.runWith(Sink.head[ByteString](), materializer))
      }
      val expected: ByteString = ByteString.fromString("<h1>Say hello to akka-http</h1>")
      result must be_==(expected).await(retries = 0, timeout = 5.seconds)
    }

    "round trip XML successfully" in withClient() { client =>
      val document = XML.fromString("""<?xml version="1.0" encoding='UTF-8'?>
                                      |<note>
                                      |  <from>hello</from>
                                      |  <to>world</to>
                                      |</note>""".stripMargin)
      document.normalizeDocument()

      toScala {
        client.url(s"http://localhost:$testServerPort").post(body(document))
      }.map { response =>
        import javax.xml.parsers.DocumentBuilderFactory
        val dbf = DocumentBuilderFactory.newInstance
        dbf.setNamespaceAware(true)
        dbf.setCoalescing(true)
        dbf.setIgnoringElementContentWhitespace(true)
        dbf.setIgnoringComments(true)
        dbf.newDocumentBuilder

        val responseXml = response.getBody(xml())
        responseXml.normalizeDocument()

        responseXml.isEqualNode(document) must beTrue and {
          response.getUri must beEqualTo(new java.net.URI(
            s"http://localhost:$testServerPort"))
        }
      }.await(retries = 0, timeout = 5.seconds)
    }
  }
}
