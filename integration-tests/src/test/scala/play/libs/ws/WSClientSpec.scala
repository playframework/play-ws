package play.libs.ws

import java.net.MalformedURLException
import java.time.Duration

import akka.stream.javadsl.Sink
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.AkkaServerProvider

import scala.compat.java8.FutureConverters._
import scala.concurrent.TimeoutException

trait WSClientSpec extends Specification
    with AkkaServerProvider
    with FutureMatchers
    with DefaultBodyWritables
    with XMLBodyWritables with XMLBodyReadables {

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

    "send patch request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .patch(body("hello world"))
          .toScala
          .map(_.getBody must be_==("PATCH hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send put request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .put(body("hello world"))
          .toScala
          .map(_.getBody must be_==("PUT hello world"))
          .awaitFor(defaultTimeout)
      }
    }

    "send delete request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .delete()
          .toScala
          .map(_.getBody must be_==("DELETE"))
          .awaitFor(defaultTimeout)
      }
    }

    "send head request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .head()
          .toScala
          .map(_.getStatus must be_==(200))
          .awaitFor(defaultTimeout)
      }
    }

    "send options request" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort")
          .options()
          .toScala
          .map(_.getBody must be_==("OPTIONS"))
          .awaitFor(defaultTimeout)
      }
    }

    "round trip XML" in {
      val document = XML.fromString(
        """<?xml version="1.0" encoding='UTF-8'?>
          |<note>
          |  <from>hello</from>
          |  <to>world</to>
          |</note>""".stripMargin)
      document.normalizeDocument()

      withClient() {
        _.url(s"http://localhost:$testServerPort/xml")
          .post(body(document))
          .toScala
          .map(_.getBody(xml()))
          .map(_.isEqualNode(document) must be_==(true))
          .awaitFor(defaultTimeout)
      }
    }

    "authenticate basic" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/auth/basic")
          .setAuth("user", "pass", WSAuthScheme.BASIC)
          .get()
          .toScala
          .map(_.getBody)
          .map(_ must be_==("Authenticated user"))
          .awaitFor(defaultTimeout)
      }
    }

    "set host header" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/virtualhost")
          .setVirtualHost("virtualhost")
          .get()
          .toScala
          .map(_.getBody must be_==("virtualhost"))
          .awaitFor(defaultTimeout)
      }
    }

    "complete after timeout" in {
      withClient() {
        _.url(s"http://localhost:$testServerPort/timeout")
          .setRequestTimeout(Duration.ofMillis(100))
          .get()
          .toScala
          .map(_ => failure)
          .recover {
            case ex =>
              // due to java/scala conversions of future, the exception
              // gets wrapped in CompletionException which we here unwrap
              val e = if (ex.getCause != null) ex.getCause else ex
              e must beAnInstanceOf[TimeoutException]
              e.getMessage must startWith("Request timeout")
              success
          }
          .awaitFor(defaultTimeout)
      }
    }
  }
}
