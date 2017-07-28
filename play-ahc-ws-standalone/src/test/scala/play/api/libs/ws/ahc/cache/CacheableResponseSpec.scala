/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc.cache

import org.specs2.mutable.Specification
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

class CacheableResponseSpec extends Specification {

  "CacheableResponse" should {

    "get body" in {

      "when it is text/plain" in {
        val response = CacheableResponse(200, "https://playframework.com/", "PlayFramework Homepage").withHeaders(HttpHeaders.Names.CONTENT_TYPE -> "text/plain")
        response.getResponseBody must beEqualTo("PlayFramework Homepage")
        response.getContentType must beEqualTo("text/plain")
      }

      "when it is application/json" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""").withHeaders("Content-Type" -> "application/json")
        response.getResponseBody must beEqualTo("""{ "a": "b" }""")
        response.getContentType must beEqualTo("application/json")
      }

      "when it is application/json; charset=utf-8" in {
        val response = CacheableResponse(200, "https://playframework.com/", """{ "a": "b" }""").withHeaders("Content-Type" -> "application/json; charset=utf-8")
        response.getResponseBody must beEqualTo("""{ "a": "b" }""")
        response.getContentType must beEqualTo("application/json; charset=utf-8")
      }
    }
  }
}
