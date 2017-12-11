/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import org.specs2.execute.Result
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

import scala.language.implicitConversions

trait WSRequestSpec extends Specification with Mockito with AfterAll with DefaultBodyReadables with DefaultBodyWritables {

  sequential

  def withClient(block: StandaloneWSClient => Result): Result
  def withRequest(url: String)(block: StandaloneWSRequest => Result): Result

  def getQueryParameters(req: StandaloneWSRequest): Seq[(String, String)]
  def getCookies(req: StandaloneWSRequest): Seq[WSCookie]
  def getHeaders(req: StandaloneWSRequest): Map[String, Seq[String]]
  def getByteData(req: StandaloneWSRequest): Array[Byte]

  "Given the full URL" in {

    "correctly URL-encode the query string part" in {
      withRequest("http://example.com") { request =>
        request.withQueryStringParameters("&" -> "=").uri.toString must equalTo("http://example.com?%26=%3D")
      }
    }

    "set all query string parameters" in {
      withRequest("http://example.com") { request =>
        request.withQueryStringParameters("bar" -> "baz").uri.toString must equalTo("http://example.com?bar=baz")
        request.withQueryStringParameters("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo("http://example.com?bar=bah&bar=baz")
      }
    }

    "discard old query parameters when setting new ones" in {
      withRequest("http://example.com") {
        _.withQueryStringParameters("bar" -> "baz")
          .withQueryStringParameters("bar" -> "bah")
          .uri.toString must equalTo("http://example.com?bar=bah")
      }
    }

    "add query string param" in {
      withRequest("http://example.com") {
        _.withQueryStringParameters("bar" -> "baz")
          .addQueryStringParameters("bar" -> "bah")
          .uri.toString must equalTo("http://example.com?bar=bah&bar=baz")
      }
    }

    "support adding several query string values for a parameter" in {
      withRequest("http://example.com") { request =>
        val newRequest = request
          .withQueryStringParameters("play" -> "foo1")
          .addQueryStringParameters("play" -> "foo2")

        newRequest.queryString.get("play") must beSome.which(_.contains("foo1"))
        newRequest.queryString.get("play") must beSome.which(_.contains("foo2"))
        newRequest.queryString.get("play") must beSome.which(_.size == 2)
      }
    }

    "support several query string values for  a parameter" in {
      withClient { client =>
        val req = client.url("http://playframework.com/")
          .withQueryStringParameters("foo" -> "foo1", "foo" -> "foo2")

        val paramsList = getQueryParameters(req)
        paramsList.exists(p => (p._1 == "foo") && (p._2 == "foo1")) must beTrue
        paramsList.exists(p => (p._1 == "foo") && (p._2 == "foo2")) must beTrue
        paramsList.count(p => p._1 == "foo") must beEqualTo(2)
      }

    }

  }

  "For Cookies" in {

    def cookie(name: String, value: String): WSCookie = DefaultWSCookie(name, value)

    "add cookies to request" in {
      withClient { client =>
        val req = client
          .url("http://example.com")
          .addCookies(cookie("cookie1", "value1"))

        getCookies(req) must size(1)
        getCookies(req).head.name must beEqualTo("cookie1")
        getCookies(req).head.value must beEqualTo("value1")
      }
    }

    "set all cookies for request" in {
      withClient { client =>
        val req = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))

        getCookies(req) must size(2)
        getCookies(req).head.name must beEqualTo("cookie1")
        getCookies(req).head.value must beEqualTo("value1")

        getCookies(req).tail.head.name must beEqualTo("cookie2")
        getCookies(req).tail.head.value must beEqualTo("value2")
      }
    }

    "keep old cookies when adding a new one" in {
      withClient { client =>
        val req = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"))
          .addCookies(cookie("cookie2", "value2"))

        getCookies(req) must size(2)
        getCookies(req).head.name must beEqualTo("cookie1")
        getCookies(req).head.value must beEqualTo("value1")

        getCookies(req).tail.head.name must beEqualTo("cookie2")
        getCookies(req).tail.head.value must beEqualTo("value2")
      }
    }

    "discard all cookies when setting new ones" in {
      withClient { client =>
        val req = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .withCookies(cookie("cookie3", "value3"), cookie("cookie4", "value4"))

        getCookies(req) must size(2)
        getCookies(req).head.name must beEqualTo("cookie3")
        getCookies(req).head.value must beEqualTo("value3")

        getCookies(req).tail.head.name must beEqualTo("cookie4")
        getCookies(req).tail.head.value must beEqualTo("value4")
      }
    }

    "set cookies through Cookie header directly" in {
      withClient { client =>
        val cookies = Seq("cookie1" -> "value1", "cookie2" -> "value2")
        val req = client
          .url("http://example.com")
          .addHttpHeaders("Cookie" -> cookies.map(c => c._1 + "=" + c._2).mkString(", "))

        getHeaders(req) must size(1)
        getHeaders(req)("Cookie").head must beEqualTo("cookie1=value1, cookie2=value2")
      }
    }
  }

  "For HTTP Headers" in {

    "support setting headers" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1", "key" -> "value2")
        getHeaders(req)("key") must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

    "discard old headers when setting" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("key1" -> "value1")
          .withHttpHeaders("key2" -> "value2")
        getHeaders(req).get("key1") must beEmpty
        getHeaders(req).get("key2") must beEqualTo(Some(Seq("value2")))
      }
    }

    "support adding headers" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1")
          .addHttpHeaders("key" -> "value2")
        getHeaders(req)("key") must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

    "keep existing headers when adding a new one" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("key1" -> "value1")
          .addHttpHeaders("key2" -> "value2")
        getHeaders(req)("key1").head must beEqualTo("value1")
        getHeaders(req)("key2").head must beEqualTo("value2")
      }
    }

    "not make Content-Type header if there is Content-Type in headers already" in {
      withClient { client =>
        val req = client.url("http://playframework.com/")
          .withHttpHeaders(HttpHeaders.Names.CONTENT_TYPE -> "fake/contenttype; charset=utf-8")
          .withBody("I am a text/plain body")
        getHeaders(req)(HttpHeaders.Names.CONTENT_TYPE).map(_.toLowerCase) must containTheSameElementsAs(Seq("fake/contenttype; charset=utf-8"))
      }
    }

    "treat headers as case insensitive" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1", "KEY" -> "value2")
        getHeaders(req)("key") must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }.pendingUntilFixed("Akka Http keeps case sensitivity")

    "get a single header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key2" -> "value2")

        req.header("Key1") must beSome("value1")
      }
    }

    "get all values for a header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        req.headerValues("Key1") must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

    "get none when header is not present" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        req.header("Non") must beNone
      }
    }

    "get an empty seq when header has no values" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        req.headerValues("Non") must beEmpty
      }
    }

    "get all the header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "value")

        req.headers("Key1") must containTheSameElementsAs(Seq("value1", "value2"))
        req.headers("Key2") must containTheSameElementsAs(Seq("value"))
      }
    }
  }

  "For requests with body" in {

    "Have form params for content type application/x-www-form-urlencoded" in {
      withClient { client =>
        val req = client.url("http://playframework.com/")
          .withBody(Map("param1" -> Seq("value1")))
        (new String(getByteData(req), "UTF-8")) must_== ("param1=value1")
      }
    }
  }

}
