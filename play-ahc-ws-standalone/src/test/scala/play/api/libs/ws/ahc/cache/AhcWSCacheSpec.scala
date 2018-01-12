/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws.ahc.cache

import java.net.URI

import com.typesafe.play.cachecontrol.HttpDate._
import com.typesafe.play.cachecontrol._
import org.joda.time.Seconds
import org.specs2.mutable.Specification
import play.shaded.ahc.io.netty.handler.codec.http.{ DefaultHttpHeaders, HttpHeaders }
import play.shaded.ahc.org.asynchttpclient.{ Request, RequestBuilder }

class AhcWSCacheSpec extends Specification {

  "freshness heuristics flag" should {

    "calculate LM freshness" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache = new AhcHttpCache(new StubHttpCache(), true)
      val url = "http://localhost:9000"

      val uri = new URI(url)
      val lastModifiedDate: String = format(now.minusHours(1))
      val request: CacheRequest = CacheRequest(uri, "GET", Map())
      val response: CacheResponse = StoredResponse(uri, 200, Map(HeaderName("Last-Modified") -> Seq(lastModifiedDate)), "GET", Map())

      val actual = cache.calculateFreshnessFromHeuristic(request, response)

      actual must beSome.which(value => value must be_==(Seconds.seconds(360))) // 0.1 hours
    }

    "be disabled when set to false" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache = new AhcHttpCache(new StubHttpCache(), false)
      val url = "http://localhost:9000"

      val uri = new URI(url)
      val lastModifiedDate: String = "Wed, 09 Apr 2008 23:55:38 GMT"
      val request: CacheRequest = CacheRequest(uri, "GET", Map())
      val response: CacheResponse = StoredResponse(uri, 200, Map(HeaderName("Last-Modified") -> Seq(lastModifiedDate)), "GET", Map())

      val actual = cache.calculateFreshnessFromHeuristic(request, response)

      actual must beNone
    }

  }

  "calculateSecondaryKeys" should {

    "calculate keys correctly" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val cache = new AhcHttpCache(new StubHttpCache(), false)

      val url = "http://localhost:9000"

      val request = generateRequest(url)(headers => headers.add("Accept-Encoding", "gzip"))
      val response = CacheableResponse(200, url).withHeaders("Vary" -> "Accept-Encoding")

      val actual = cache.calculateSecondaryKeys(request, response)

      actual must beSome.which { d =>
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
