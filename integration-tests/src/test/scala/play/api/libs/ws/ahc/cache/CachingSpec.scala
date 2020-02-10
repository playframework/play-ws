/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.AkkaServerProvider
import play.api.libs.ws.ahc._
import play.shaded.ahc.org.asynchttpclient._

import scala.concurrent.Future

class CachingSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with AkkaServerProvider
    with AfterAll
    with FutureMatchers
    with Mockito {

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

  override def afterAll = {
    super.afterAll()
    asyncHttpClient.close()
  }

  "GET" should {

    "work once" in {
      val cache = mock[Cache]
      cache.get(any[EffectiveURIKey]()).returns(Future.successful(None))

      val cachingAsyncHttpClient = new CachingAsyncHttpClient(asyncHttpClient, new AhcHttpCache(cache))
      val ws                     = new StandaloneAhcWSClient(cachingAsyncHttpClient)

      ws.url(s"http://localhost:$testServerPort/hello")
        .get()
        .map { response =>
          response.body must be_==("<h1>Say hello to akka-http</h1>")
        }
        .await

      there.was(one(cache).get(EffectiveURIKey("GET", new java.net.URI(s"http://localhost:$testServerPort/hello"))))
    }
  }
}
