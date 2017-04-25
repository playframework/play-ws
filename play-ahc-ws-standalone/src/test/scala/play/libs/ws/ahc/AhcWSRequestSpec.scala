/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc

import play.shaded.ahc.org.asynchttpclient.{ Request, RequestBuilderBase, SignatureCalculator }
import org.specs2.mock.Mockito
import org.specs2.mutable._
import play.libs.ws.{ WSAuthScheme, WSCookie, WSSignatureCalculator }
import play.libs.oauth.OAuth
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

import scala.collection.JavaConverters._

class AhcWSRequestSpec extends Specification with Mockito {

  "AhcWSRequest" should {

    "Have GET method as the default" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.buildRequest().getMethod must be_==("GET")
    }

    "Set virtualHost appropriately" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setVirtualHost("foo.com")
      val actual = request.buildRequest().getVirtualHost()
      actual must beEqualTo("foo.com")
    }

    "For POST requests" in {

      "set text/plain content-types for text bodies" in {
        val client = mock[StandaloneAhcWSClient]
        val formEncoding = java.net.URLEncoder.encode("param1=value1", "UTF-8")

        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody("HELLO WORLD")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getStringData must be_==("HELLO WORLD")
      }

      "keep existent content type when setting body" in {
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("application/x-www-form-urlencoded") // set content type by hand
          .setBody("HELLO WORLD") // and body is set to string (see #5221)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getHeaders.get(HttpHeaders.Names.CONTENT_TYPE) must be_==("application/x-www-form-urlencoded") // preserve the content type
        req.getStringData must be_==("HELLO WORLD") // should result in byte data.
      }

      "have form params when content-type application/x-www-form-urlencoded and signed" in {
        import scala.collection.JavaConverters._
        val client = mock[StandaloneAhcWSClient]
        val consumerKey = new OAuth.ConsumerKey("key", "secret")
        val token = new OAuth.RequestToken("token", "secret")
        val calc = new OAuth.OAuthCalculator(consumerKey, token)
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("application/x-www-form-urlencoded") // set content type by hand
          .setBody("param1=value1")
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        // Note we use getFormParams instead of getByteData here.
        req.getFormParams.asScala must containTheSameElementsAs(List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")))
      }

      "remove a user defined content length header if we are parsing body explicitly when signed" in {
        import scala.collection.JavaConverters._
        val client = mock[StandaloneAhcWSClient]
        val consumerKey = new OAuth.ConsumerKey("key", "secret")
        val token = new OAuth.RequestToken("token", "secret")
        val calc = new OAuth.OAuthCalculator(consumerKey, token)
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("application/x-www-form-urlencoded") // set content type by hand
          .setBody("param1=value1")
          .addHeader("Content-Length", "9001") // add a meaningless content length here...
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        val headers = req.getHeaders
        req.getFormParams.asScala must containTheSameElementsAs(List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")))
        headers.get("Content-Length") must beNull // no content length!
      }
    }

    "Use a custom signature calculator" in {
      val client = mock[StandaloneAhcWSClient]
      var called = false
      val calc = new SignatureCalculator with WSSignatureCalculator {
        override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
          called = true
        }
      }
      val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
        .sign(calc)
        .buildRequest()
      called must beTrue
    }

    "support setting a request timeout" in {
      requestWithTimeout(1000) must beEqualTo(1000)
    }

    "support setting an infinite request timeout" in {
      requestWithTimeout(-1) must beEqualTo(-1)
    }

    "not support setting a request timeout < -1" in {
      requestWithTimeout(-2) must throwA[IllegalArgumentException]
    }

    "not support setting a request timeout > Integer.MAX_VALUE" in {
      requestWithTimeout(Int.MaxValue.toLong + 1) must throwA[IllegalArgumentException]
    }

    "only send first content type header and add charset=utf-8 to the Content-Type header if it's manually adding but lacking charset" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody("HELLO WORLD")
      request.setHeader("Content-Type", "application/json")
      request.setHeader("Content-Type", "application/xml")
      val req = request.buildRequest()
      req.getHeaders.get("Content-Type") must be_==("application/json")
    }

    "only send first content type header and keep the charset if it has been set manually with a charset" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody("HELLO WORLD")
      request.setHeader("Content-Type", "application/json; charset=US-ASCII")
      request.setHeader("Content-Type", "application/xml")
      val req = request.buildRequest()
      req.getHeaders.get("Content-Type") must be_==("application/json; charset=US-ASCII")
    }

    "Set Realm.UsePreemptiveAuth to false when WSAuthScheme.DIGEST being used" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.DIGEST)
      val req = request.buildRequest()
      req.getRealm.isUsePreemptiveAuth must beFalse
    }

    "Set Realm.UsePreemptiveAuth to true when WSAuthScheme.DIGEST not being used" in {
      val client = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.BASIC)
      val req = request.buildRequest()
      req.getRealm.isUsePreemptiveAuth must beTrue
    }

    "For HTTP Headers" in {

      "add a new header" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        request
          .addHeader("header1", "value1")
          .buildRequest()
          .getHeaders.get("header1") must beEqualTo("value1")
      }

      "add new value for existent header" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        request.getHeaders.get("header1").asScala must contain("value1")
        request.getHeaders.get("header1").asScala must contain("value2")

        val ahcRequest = request.buildRequest()

        ahcRequest.getHeaders.getAll("header1").asScala must contain("value1")
        ahcRequest.getHeaders.getAll("header1").asScala must contain("value2")
      }

      "set all headers" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        request
          .setHeaders(Map(
            "header1" -> Seq("value1").asJava,
            "header2" -> Seq("value2").asJava).asJava
          )
          .buildRequest()
          .getHeaders
          .get("header1") must beEqualTo("value1")
      }

      "keep existent headers when adding a new one" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        val ahcReq = request
          .addHeader("header1", "value1")
          .addHeader("header2", "value2")
          .buildRequest()

        ahcReq.getHeaders.get("header1") must beEqualTo("value1")
        ahcReq.getHeaders.get("header2") must beEqualTo("value2")
      }

      "treat header names case insensitively" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("HEADER1", "value2")
          .buildRequest()

        request.getHeaders.getAll("header1").asScala must contain("value1")
        request.getHeaders.getAll("header1").asScala must contain("value2")
      }

    }

    "For query string parameters" in {

      "add query string parameter" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .buildRequest()

        request.getUrl must contain("p1=v1")
      }

      "add new value for existent parameter" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p1", "v2")
          .buildRequest()

        request.getUrl must contain("p1=v1")
        request.getUrl must contain("p1=v2")
      }

      "keep existent parameters when adding a new one" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .buildRequest()

        request.getUrl must contain("p1=v1")
        request.getUrl must contain("p2=v2")
      }

      "set all the parameters" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .setQueryString(Map(
            "p1" -> Seq("v1").asJava,
            "p2" -> Seq("v2").asJava).asJava
          )
          .buildRequest()

        request.getUrl must contain("p1=v1")
        request.getUrl must contain("p2=v2")
      }

      "set a query string appropriately" in {
        val queryParams = requestWithQueryString("q=playframework&src=typd")
        queryParams.size must beEqualTo(2)
        queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")) must beTrue
        queryParams.exists(p => (p.getName == "src") && (p.getValue == "typd")) must beTrue
      }

      "support several query string values for a parameter" in {
        val queryParams = requestWithQueryString("q=scala&q=playframework&q=fp")
        queryParams.size must beEqualTo(3)
        queryParams.exists(p => (p.getName == "q") && (p.getValue == "scala")) must beTrue
        queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")) must beTrue
        queryParams.exists(p => (p.getName == "q") && (p.getValue == "fp")) must beTrue
        queryParams.count(p => p.getName == "q") must beEqualTo(3)
      }

      "support a query string parameter without value" in {
        val queryParams = requestWithQueryString("q=playframework&src=")
        queryParams.size must beEqualTo(2)
        queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")) must beTrue
        queryParams.exists(p => (p.getName.equals("src")) && (p.getValue == null)) must beTrue
      }

      "not support a query string with more than 2 = per part" in {
        requestWithQueryString("q=scala=playframework&src=typd") must throwA[RuntimeException]
      }

      "not support a query string if it starts with = and is empty" in {
        requestWithQueryString("=&src=typd") must throwA[RuntimeException]
      }
    }

    "For Cookies" in {

      def cookie(name: String, value: String): WSCookie = {
        new AhcWSCookie(
          play.shaded.ahc.org.asynchttpclient.cookie.Cookie.newValidCookie(name, value, true, "example.com", "/", 1000, true, true)
        )
      }

      "add a new cookie" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .buildRequest()

        request.getCookies.asScala.head.getName must beEqualTo("cookie1")
      }

      "add more than one cookie" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.getName must beEqualTo("cookie1")
        request.getCookies.asScala(1).getName must beEqualTo("cookie2")
      }

      "keep existent cookies when adding a new one" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .addCookie(cookie("cookie2", "value2"))
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.getName must beEqualTo("cookie1")
        request.getCookies.asScala(1).getName must beEqualTo("cookie2")
      }

      "set all cookies" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .setCookies(List(cookie("cookie1", "value1"), cookie("cookie2", "value2")).asJava)
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.getName must beEqualTo("cookie1")
        request.getCookies.asScala(1).getName must beEqualTo("cookie2")
      }

      "discard old cookies when setting" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .setCookies(List(cookie("cookie3", "value1"), cookie("cookie4", "value2")).asJava)
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.getName must beEqualTo("cookie3")
        request.getCookies.asScala(1).getName must beEqualTo("cookie4")
      }
    }
  }

  def requestWithTimeout(timeout: Long) = {
    val client = mock[StandaloneAhcWSClient]
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setRequestTimeout(timeout)
    request.buildRequest().getRequestTimeout()
  }

  def requestWithQueryString(query: String) = {
    import scala.collection.JavaConverters._
    val client = mock[StandaloneAhcWSClient]
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setQueryString(query)
    val queryParams = request.buildRequest().getQueryParams
    queryParams.asScala
  }
}
