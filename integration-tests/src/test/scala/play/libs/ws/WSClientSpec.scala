package play.libs.ws

import java.net.MalformedURLException

import akka.stream.javadsl.Sink
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.compat.java8.FutureConverters._

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with DefaultBodyWritables {

  implicit def executionEnv: ExecutionEnv

  def withClient()(block: StandaloneWSClient => Result): Result

  override val routes = play.api.libs.ws.WSClientSpec.routes

  "url" should {
    "throw an exception on invalid url" in {
      withClient() { client =>
        { client.url("localhost") } must (throwAn[RuntimeException] like {
          case ex: RuntimeException =>
            ex.getCause must beAnInstanceOf[MalformedURLException]
        })
      }
    }

    "not throw exception on valid url" in {
      withClient() { client =>
        { client.url(s"http://localhost:$testServerPort") } must not(throwAn[IllegalArgumentException])
      }
    }
  }

  "WSClient" should {

    "request a url as an in memory string" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .toScala
          .map(_.getBody)
          .map(_ must beEqualTo("GET "))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a Foo" in {
      case class Foo(body: String)

      val fooBodyReadable = new BodyReadable[Foo] {
        override def apply(t: StandaloneWSResponse): Foo = Foo(t.getBody)
      }

      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .get()
          .toScala
          .map(_.getBody(fooBodyReadable))
          .map(_ must beEqualTo(Foo("GET ")))
          .awaitFor(defaultTimeout)
      }
    }

    "request a url as a stream" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/index")
          .stream()
          .toScala
          .map(_.getBodyAsSource)
          .flatMap(_.runWith(Sink.head(), materializer).toScala)
          .map(_.utf8String must beEqualTo("GET "))
          .awaitFor(defaultTimeout)
      }
    }

    "send post request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .post(body("hello world"))
          .toScala
          .map(_.getBody must be_==("POST hello world"))
          .awaitFor(defaultTimeout)
      }
    }
  }

  //  "play.libs.ws.ahc.StandaloneAhcWSClient" should {
  //
  //    "get successfully" in {
  //      def someOtherMethod(string: String) = {
  //        new InMemoryBodyWritable(akka.util.ByteString.fromString(string), "text/plain")
  //      }
  //
  //      withClient() {
  //        _.url(s"http://localhost:$testServerPort")
  //          .post(someOtherMethod("hello world"))
  //          .toScala
  //          .map(_.getBody() must be_==("hello world"))
  //          .awaitFor(defaultTimeout)
  //      }
  //    }
  //
  //    "source successfully" in {
  //      withClient() {
  //        _.url(s"http://localhost:$testServerPort")
  //          .stream()
  //          .toScala
  //          .flatMap(_.getBodyAsSource.runWith(Sink.head(), materializer).toScala)
  //          .map(_ must be_== (ByteString.fromString("<h1>Say hello to akka-http</h1>")))
  //      }
  //    }

  //    "round trip XML successfully" in {
  //      val document = XML.fromString("""<?xml version="1.0" encoding='UTF-8'?>
  //                                      |<note>
  //                                      |  <from>hello</from>
  //                                      |  <to>world</to>
  //                                      |</note>""".stripMargin)
  //      document.normalizeDocument()
  //
  //      toScala {
  //        client.url(s"http://localhost:$testServerPort").post(body(document))
  //      }.map { response =>
  //        import javax.xml.parsers.DocumentBuilderFactory
  //        val dbf = DocumentBuilderFactory.newInstance
  //        dbf.setNamespaceAware(true)
  //        dbf.setCoalescing(true)
  //        dbf.setIgnoringElementContentWhitespace(true)
  //        dbf.setIgnoringComments(true)
  //        val db = dbf.newDocumentBuilder
  //
  //        val responseXml = response.getBody(xml())
  //        responseXml.normalizeDocument()
  //
  //        responseXml.isEqualNode(document) must beTrue
  //      }.await(retries = 0, timeout = 5.seconds)
  //    }

  //}
}
