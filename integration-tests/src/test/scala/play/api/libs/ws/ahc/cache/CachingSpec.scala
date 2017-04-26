/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
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

import scala.collection.mutable

class CachingSpec(implicit val executionEnv: ExecutionEnv) extends Specification with AkkaServerProvider with AfterAll with FutureMatchers with Mockito {

  val asyncHttpClient: AsyncHttpClient = {
    val config = AhcWSClientConfigFactory.forClientConfig()
    val ahcConfig: AsyncHttpClientConfig = new AhcConfigBuilder(config).build()
    new DefaultAsyncHttpClient(ahcConfig)
  }

  override val routes: Route = {
    import akka.http.scaladsl.server.Directives._
    respondWithHeader(RawHeader("Cache-Control", "public")) {
      val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
      complete(httpEntity)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    asyncHttpClient.close()
  }

  "GET" should {

    "work once" in {
      val cache = mock[Cache]
      val cachingAsyncHttpClient = new CachingAsyncHttpClient(asyncHttpClient, cache, scala.concurrent.ExecutionContext.global)
      val ws = new StandaloneAhcWSClient(cachingAsyncHttpClient)

      ws.url(s"http://localhost:$testServerPort/").get().map { response =>
        response.body must be_==("<h1>Say hello to akka-http</h1>")
      }.await

      there was one(cache).get(EffectiveURIKey("GET", new java.net.URI(s"http://localhost:$testServerPort/")))
    }

  }
}

class StubHttpCache extends Cache {

  private val underlying = new mutable.HashMap[EffectiveURIKey, ResponseEntry]()

  override def remove(key: EffectiveURIKey): Unit = underlying.remove(key)

  override def put(key: EffectiveURIKey, entry: ResponseEntry): Unit = underlying.put(key, entry)

  override def get(key: EffectiveURIKey): ResponseEntry = underlying.get(key).orNull

  override def close(): Unit = {

  }

}