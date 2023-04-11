/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.AkkaServerProvider
import play.api.libs.ws.ahc._
import play.api.libs.ws.DefaultBodyReadables._
import play.shaded.ahc.org.asynchttpclient._

import scala.concurrent.Future
import scala.reflect.ClassTag

class CachingSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with AkkaServerProvider
    with AfterAll
    with FutureMatchers {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  val asyncHttpClient: AsyncHttpClient = {
    val config                           = AhcWSClientConfigFactory.forClientConfig()
    val ahcConfig: AsyncHttpClientConfig = new AhcConfigBuilder(config).build()
    new DefaultAsyncHttpClient(ahcConfig)
  }

  override val routes: Route = {
    import akka.http.scaladsl.server.Directives._
    path("hello") {
      respondWithHeader(RawHeader("Cache-Control", "public")) {
        val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
        complete(httpEntity)
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    asyncHttpClient.close()
  }

  "GET" should {

    "work once" in {
      val cache = mock[Cache]
      when(cache.get(any[EffectiveURIKey]())).thenReturn(Future.successful(None))

      val cachingAsyncHttpClient = new CachingAsyncHttpClient(asyncHttpClient, new AhcHttpCache(cache))
      val ws                     = new StandaloneAhcWSClient(cachingAsyncHttpClient)

      ws.url(s"http://localhost:$testServerPort/hello")
        .get()
        .map { response =>
          response.body[String] must be_==("<h1>Say hello to akka-http</h1>")
        }
        .await

      Mockito.verify(cache).get(EffectiveURIKey("GET", new java.net.URI(s"http://localhost:$testServerPort/hello")))
      success
    }
  }
}
