/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import java.io.File

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.AkkaServerProvider

import scala.concurrent._

class AhcWSClientSpec(implicit val executionEnv: ExecutionEnv) extends Specification with AkkaServerProvider with AfterAll with FutureMatchers {

  def withClient(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Result): Result = {
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }

  private val gzipFile = {
    val path = this.getClass.getClassLoader.getResource("file.txt.gz").getPath
    new File(path)
  }

  override val routes: Route = {
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server.directives.ContentTypeResolver.Default
    path("file") {
      getFromFile(gzipFile)
    } ~
      path("index") {
        respondWithHeader(RawHeader("Cache-Control", "public")) {
          val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
          complete(httpEntity)
        }
      }
  }

  "url" should {
    "throw an exception on invalid url" in {
      withClient() { client =>
        { client.url("localhost") } must throwAn[IllegalArgumentException]
      }
    }

    "not throw exception on valid url" in {
      withClient() { client =>
        { client.url(s"http://localhost:$testServerPort") } must not(throwAn[IllegalArgumentException])
      }
    }
  }

  "WSClient" should {

    "request a url" in {
      withClient() { client =>
        val result = Await.result(client.url(s"http://localhost:$testServerPort/index").get().map(res => res.body), defaultTimeout)
        result must beEqualTo("<h1>Say hello to akka-http</h1>")
      }
    }

    "request a file" in {
      withClient() { client =>
        val result = Await.result(client.url(s"http://localhost:$testServerPort/file").get().map(res => res.body), defaultTimeout)
        result.trim must beEqualTo("This is a gzipped file")
      }
    }

    "request a gzipped file" in {

      "strip the Content-Encoding by default" in {
        withClient() { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/file").get(), defaultTimeout)
          result.header("Content-Encoding") must beNone
          result.body.trim must beEqualTo("This is a gzipped file")
        }
      }

      "keeps the Content-Encoding when configured to do so" in {

        val config = AhcWSClientConfigFactory.forConfig().copy(keepEncodingHeader = true)

        withClient(config) { client =>
          val result = Await.result(client.url(s"http://localhost:$testServerPort/file").get(), defaultTimeout)
          result.header("Content-Encoding") must beSome("gzip")
          result.body.trim must beEqualTo("This is a gzipped file")
        }
      }

    }
  }
}
