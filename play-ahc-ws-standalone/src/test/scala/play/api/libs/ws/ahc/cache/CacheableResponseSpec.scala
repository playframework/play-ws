/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import org.specs2.mutable.Specification
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names._
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig

class CacheableResponseSpec extends Specification {
  val achConfig = new DefaultAsyncHttpClientConfig.Builder().build()

  "CacheableResponse" should {

    "get body" in {

      "when it is text/plain" in {
        val response = CacheableResponse(200, "https://playframework.com/", "PlayFramework Homepage", achConfig)
          .withHeaders(CONTENT_TYPE -> "text/plain")
        response.getResponseBody must beEqualTo("PlayFramework Homepage")
        response.getContentType must beEqualTo("text/plain")
      }

      "when it is application/json" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""", achConfig).withHeaders(
          "Content-Type" -> "application/json"
        )
        response.getResponseBody must beEqualTo("""{ "a": "b" }""")
        response.getContentType must beEqualTo("application/json")
      }

      "when it is application/json; charset=utf-8" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""", achConfig).withHeaders(
          "Content-Type" -> "application/json; charset=utf-8"
        )
        response.getResponseBody must beEqualTo("""{ "a": "b" }""")
        response.getContentType must beEqualTo("application/json; charset=utf-8")
      }
    }
  }
}
