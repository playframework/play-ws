/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.net.URI

import org.playframework.cachecontrol.HttpDate._
import org.playframework.cachecontrol._
import org.specs2.mutable.Specification
import play.shaded.ahc.org.asynchttpclient.AsyncHandler
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.Request
import play.shaded.ahc.org.asynchttpclient.RequestBuilder

class AhcWSCacheSpec extends Specification {

  "CachingAsyncHttpClient" should {
    "reject unknown async handler types" in {
      val handler = new AsyncHandler[Unit] {
        override def onStatusReceived(status: play.shaded.ahc.org.asynchttpclient.HttpResponseStatus) =
          AsyncHandler.State.CONTINUE
        override def onHeadersReceived(headers: HttpHeaders) = AsyncHandler.State.CONTINUE
        override def onBodyPartReceived(part: play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart) =
          AsyncHandler.State.CONTINUE
        override def onThrowable(throwable: Throwable): Unit = ()
        override def onCompleted(): Unit                     = ()
      }
      val client = new CachingAsyncHttpClient(null.asInstanceOf[AsyncHttpClient], null)

      client.executeRequest(generateRequest("http://localhost/")(_.clear()), handler) must
        throwA[IllegalStateException]("Unknown handler type")
    }
  }

  "freshness heuristics flag" should {

    "calculate LM freshness" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache: AhcHttpCache = new AhcHttpCache(new StubHttpCache(), true)
      val url                          = "http://localhost:9000"

      val uri                      = new URI(url)
      val lastModifiedDate: String = format(now.minusHours(1))
      val request: CacheRequest    = CacheRequest(uri, "GET", Map())
      val response: CacheResponse  =
        StoredResponse(uri, 200, Map(HeaderName("Last-Modified") -> Seq(lastModifiedDate)), "GET", Map())

      val actual = cache.calculateFreshnessFromHeuristic(request, response)

      actual must beSome[Seconds].which { case value =>
        value must be_==(Seconds.seconds(360)) // 0.1 hours
      }
    }

    "be disabled when set to false" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache: AhcHttpCache = new AhcHttpCache(new StubHttpCache(), false)
      val url                          = "http://localhost:9000"

      val uri                      = new URI(url)
      val lastModifiedDate: String = "Wed, 09 Apr 2008 23:55:38 GMT"
      val request: CacheRequest    = CacheRequest(uri, "GET", Map())
      val response: CacheResponse  =
        StoredResponse(uri, 200, Map(HeaderName("Last-Modified") -> Seq(lastModifiedDate)), "GET", Map())

      val actual = cache.calculateFreshnessFromHeuristic(request, response)

      actual must beNone
    }

  }

  "calculateSecondaryKeys" should {

    "calculate keys correctly" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache: AhcHttpCache = new AhcHttpCache(new StubHttpCache(), false)
      val achConfig                    = new DefaultAsyncHttpClientConfig.Builder().build()

      val url = "http://localhost:9000"

      val request  = generateRequest(url)(headers => headers.add("Accept-Encoding", "gzip"))
      val response = CacheableResponse(200, url, achConfig).withHeaders("Vary" -> "Accept-Encoding")

      val actual = cache.calculateSecondaryKeys(request, response)

      actual must beSome[Map[HeaderName, Seq[String]]].which { d =>
        d must haveKey(HeaderName("Accept-Encoding"))
        d(HeaderName("Accept-Encoding")) must be_==(Seq("gzip"))
      }

    }

  }

  def generateRequest(url: String)(block: HttpHeaders => HttpHeaders): Request = {
    val requestBuilder = new RequestBuilder()
    val requestHeaders = block(new DefaultHttpHeaders())

    requestBuilder
      .setUrl(url)
      .setHeaders(requestHeaders)
      .build
  }

}
