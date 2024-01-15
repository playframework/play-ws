/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import org.scalatest.wordspec.AnyWordSpec
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names._
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig

class CacheableResponseSpec extends AnyWordSpec {
  val achConfig = new DefaultAsyncHttpClientConfig.Builder().build()

  "CacheableResponse" should {

    "get body" should {

      "when it is text/plain" in {
        val response = CacheableResponse(200, "https://playframework.com/", "PlayFramework Homepage", achConfig)
          .withHeaders(CONTENT_TYPE -> "text/plain")
        assert(response.getResponseBody == "PlayFramework Homepage")
        assert(response.getContentType == "text/plain")
      }

      "when it is application/json" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""", achConfig).withHeaders(
          "Content-Type" -> "application/json"
        )
        assert(response.getResponseBody == """{ "a": "b" }""")
        assert(response.getContentType == "application/json")
      }

      "when it is application/json; charset=utf-8" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""", achConfig).withHeaders(
          "Content-Type" -> "application/json; charset=utf-8"
        )
        assert(response.getResponseBody == """{ "a": "b" }""")
        assert(response.getContentType == "application/json; charset=utf-8")
      }
    }
  }
}
