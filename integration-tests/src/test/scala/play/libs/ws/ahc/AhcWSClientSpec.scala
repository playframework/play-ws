/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.javadsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.libs.ws._

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class AhcWSClientSpec(implicit executionEnv: ExecutionEnv) extends Specification with AfterAll with FutureMatchers with XMLBodyWritables with XMLBodyReadables {
  val testServerPort = 49134

  sequential

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client with no cache
  val client = StandaloneAhcWSClient.create(
    AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader),
    null,
    materializer
  )

  private val route = {
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

  private val futureServer = {
    Http().bindAndHandle(route, "localhost", testServerPort)
  }

  override def afterAll = {
    futureServer.foreach(_.unbind)
    client.close()
    system.terminate()
  }

  "play.libs.ws.ahc.StandaloneAhcWSClient" should {

    "get successfully" in {
      def someOtherMethod(string: String) = {
        new InMemoryBodyWritable(akka.util.ByteString.fromString(string), "text/plain")
      }
      toScala(client.url(s"http://localhost:$testServerPort").post(someOtherMethod("hello world"))).map(response =>
        response.getBody() must be_==("hello world")
      ).await(retries = 0, timeout = 5.seconds)
    }

    "source successfully" in {
      val future = toScala(client.url(s"http://localhost:$testServerPort").stream())
      val result: Future[ByteString] = future.flatMap { response: StandaloneWSResponse =>
        toScala(response.getBodyAsSource.runWith(Sink.head(), materializer))
      }
      val expected: ByteString = ByteString.fromString("<h1>Say hello to akka-http</h1>")
      result must be_==(expected).await(retries = 0, timeout = 5.seconds)
    }

    "round trip XML successfully" in {
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

        val responseXml = response.getBody(xml())
        responseXml.normalizeDocument()

        responseXml.isEqualNode(document) must beTrue
      }.await(retries = 0, timeout = 5.seconds)
    }

  }

}
