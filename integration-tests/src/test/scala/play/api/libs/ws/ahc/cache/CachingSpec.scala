/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.wordspec.AnyWordSpec
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.ahc._
import play.api.libs.ws.DefaultBodyReadables._
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.api.routing.sird._
import play.shaded.ahc.org.asynchttpclient._

import scala.concurrent.Future
import scala.reflect.ClassTag

class CachingSpec extends AnyWordSpec with NettyServerProvider {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  val asyncHttpClient: AsyncHttpClient = {
    val config                           = AhcWSClientConfigFactory.forClientConfig()
    val ahcConfig: AsyncHttpClientConfig = new AhcConfigBuilder(config).build()
    new DefaultAsyncHttpClient(ahcConfig)
  }

  def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = { case GET(p"/hello") =>
    components.defaultActionBuilder(
      Results
        .Ok(<h1>Say hello to play</h1>)
        .withHeaders(("Cache-Control", "public"))
    )
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
          assert(response.body[String] == "<h1>Say hello to play</h1>")
        }
        .futureValue

      Mockito.verify(cache).get(EffectiveURIKey("GET", new java.net.URI(s"http://localhost:$testServerPort/hello")))
    }
  }
}
