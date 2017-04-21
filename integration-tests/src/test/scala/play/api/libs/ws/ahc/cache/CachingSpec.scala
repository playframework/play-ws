/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws.ahc.cache

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.ws.ahc._
import play.shaded.ahc.org.asynchttpclient._

import scala.collection.mutable

/**
 *
 */
class CachingSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers {

  sequential

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  val asyncHttpClient: AsyncHttpClient = {
    val config = AhcWSClientConfigFactory.forClientConfig()
    val ahcConfig: AsyncHttpClientConfig = new AhcConfigBuilder(config).build()
    new DefaultAsyncHttpClient(ahcConfig)
  }

  private val route: Route = {
    import akka.http.scaladsl.server.Directives._
    respondWithHeader(RawHeader("Cache-Control", "public")) {
      val httpEntity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
      complete(httpEntity)
    }
  }

  private val futureServer = {
    Http().bindAndHandle(route, "localhost", port = 9000)
  }

  override def afterAll = {
    futureServer.foreach(_.unbind())(materializer.executionContext)
    asyncHttpClient.close()
    system.terminate()
  }

  "GET" should {

    "work once" in {
      val cache = new StubHttpCache()
      val cachingAsyncHttpClient = new CachingAsyncHttpClient(asyncHttpClient, cache, scala.concurrent.ExecutionContext.global)
      val ws = new StandaloneAhcWSClient(cachingAsyncHttpClient)

      ws.url("http://localhost:9000/").get().map { response =>
        response.body must be_==("<h1>Say hello to akka-http</h1>")
      }.await
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
