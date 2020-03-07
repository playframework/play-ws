/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import java.time.Duration
import java.util.Collections

import org.specs2.mock.Mockito
import org.specs2.mutable._
import play.libs.oauth.OAuth
import play.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient.Request
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyType

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters._

class AhcWSRequestSpec extends Specification with Mockito with DefaultBodyReadables with DefaultBodyWritables {

  "AhcWSRequest" should {

    "Have GET method as the default" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.getMethod must be_==("GET")
      request.buildRequest().getMethod must be_==("GET")
    }

    "Set virtualHost appropriately" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setVirtualHost("foo.com")
      val actual = request.buildRequest().getVirtualHost()
      actual must beEqualTo("foo.com")
    }

    "set the url" in {
      val client = mock[StandaloneAhcWSClient]
      val req    = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      (req.getUrl must be_===("http://playframework.com/")).and {
        val setReq = req.setUrl("http://example.com")
        (setReq.getUrl must be_===("http://example.com")).and {
          setReq must be_===(req)
        }
      }
    }

    "For POST requests" in {

      "get method" in {
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setMethod("POST")

        req.getMethod must be_===("POST")
      }

      "set text/plain content-types for text bodies" in {
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body("HELLO WORLD"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getStringData must be_==("HELLO WORLD")
      }

      "sets content type based on a body when its not explicitly set" in {
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body("HELLO WORLD")) // set body with a content type
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getHeaders.get(HttpHeaderNames.CONTENT_TYPE) must be_==("text/plain; charset=UTF-8")
        req.getStringData must be_==("HELLO WORLD")
      }

      "keep existing content type when setting body" in {
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("text/plain+hello") // set content type by hand
          .setBody(body("HELLO WORLD"))       // and body is set to string (see #5221)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        // preserve the content type
        req.getHeaders.get(HttpHeaderNames.CONTENT_TYPE) must be_==(
          "text/plain+hello; charset=UTF-8"
        )
        // should result in byte data.
        req.getStringData must be_==("HELLO WORLD")
      }

      "have form params when passing in map" in {
        import scala.collection.JavaConverters._
        val client = mock[StandaloneAhcWSClient]
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body(Collections.singletonMap("param1", "value1")))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        // Must set the form url encoding autoomatically.
        req.getHeaders.get("Content-Type") must be_==("application/x-www-form-urlencoded")

        // Note we use getFormParams instead of getByteData here.
        req.getFormParams.asScala must containTheSameElementsAs(
          List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1"))
        )
      }

      "have form params when content-type application/x-www-form-urlencoded and signed" in {
        import scala.collection.JavaConverters._
        val client      = mock[StandaloneAhcWSClient]
        val consumerKey = new OAuth.ConsumerKey("key", "secret")
        val token       = new OAuth.RequestToken("token", "secret")
        val calc        = new OAuth.OAuthCalculator(consumerKey, token)
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("application/x-www-form-urlencoded") // set content type by hand
          .setBody(body("param1=value1"))
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        // Note we use getFormParams instead of getByteData here.
        req.getFormParams.asScala must containTheSameElementsAs(
          List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1"))
        )
      }

      "remove a user defined content length header if we are parsing body explicitly when signed" in {
        import scala.collection.JavaConverters._
        val client      = mock[StandaloneAhcWSClient]
        val consumerKey = new OAuth.ConsumerKey("key", "secret")
        val token       = new OAuth.RequestToken("token", "secret")
        val calc        = new OAuth.OAuthCalculator(consumerKey, token)
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("application/x-www-form-urlencoded") // set content type by hand
          .setBody(body("param1=value1"))
          .addHeader("Content-Length", "9001") // add a meaningless content length here...
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        val headers = req.getHeaders
        req.getFormParams.asScala must containTheSameElementsAs(
          List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1"))
        )
        headers.get("Content-Length") must beNull // no content length!
      }

    }

    "Use a proxy server" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      val proxyServer = DefaultWSProxyServer
        .builder()
        .withHost("localhost")
        .withPort(8080)
        .withPrincipal("principal")
        .withPassword("password")
        .withProxyType("socksv5")
        .withNonProxyHosts(java.util.Arrays.asList("derp"))
        .build()

      val req = request
        .setProxyServer(proxyServer)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      val actual = req.getProxyServer

      (actual.getHost must be).equalTo("localhost")
      (actual.getPort must be).equalTo(8080)
      (actual.getRealm.getPrincipal must be).equalTo("principal")
      (actual.getRealm.getPassword must be).equalTo("password")
      (actual.getRealm.getScheme must be).equalTo(AuthScheme.BASIC)
      (actual.getProxyType must be).equalTo(ProxyType.SOCKS_V5)
      (actual.getNonProxyHosts.asScala must contain("derp"))
    }

    "Use a custom signature calculator" in {
      val client = mock[StandaloneAhcWSClient]
      var called = false
      val calc = new SignatureCalculator with WSSignatureCalculator {
        override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
          called = true
        }
      }
      new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
        .sign(calc)
        .buildRequest()
      called must beTrue
    }

    "setRequestTimeout(java.time.Duration)" should {

      "support setting a request timeout to a duration" in {
        requestWithTimeout(Duration.ofSeconds(1)) must beEqualTo(1000)
      }

      "support setting a request timeout duration to infinite using -1" in {
        requestWithTimeout(Duration.ofMillis(-1)) must beEqualTo(-1)
      }

      "support setting a request timeout duration to infinite using any negative duration" in {
        requestWithTimeout(Duration.ofMillis(-2)) must beEqualTo(-1)
        requestWithTimeout(Duration.ofMillis(-15)) must beEqualTo(-1)
        requestWithTimeout(Duration.ofSeconds(-1)) must beEqualTo(-1)
        requestWithTimeout(Duration.ofMillis(java.lang.Integer.MIN_VALUE)) must beEqualTo(-1)
      }

      "support setting a request timeout duration to Long.MAX_VALUE as infinite" in {
        requestWithTimeout(Duration.ofMillis(java.lang.Long.MAX_VALUE)) must beEqualTo(-1)
      }

      "not support setting a request timeout to null" in {
        requestWithTimeout(null) must throwA[IllegalArgumentException]
      }
    }

    "allow adding an explicit Content-Type header if the BodyWritable doesn't set the Content-Type" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody(body("HELLO WORLD", null))            // content type is not set
      request.addHeader("Content-Type", "application/json") // will be used as content type is not set with a body
      val req = request.buildRequest()
      req.getHeaders.get("Content-Type") must be_==("application/json")
    }

    "ignore explicit Content-Type header if the BodyWritable already set the Content-Type" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody(body("HELLO WORLD"))
      request.addHeader("Content-Type", "application/json") // will be ignored since body already sets content type
      val req = request.buildRequest()
      req.getHeaders.get("Content-Type") must be_==("text/plain; charset=UTF-8")
    }

    "only send first Content-Type header and keep the charset when setting the Content-Type multiple times" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.addHeader("Content-Type", "application/json; charset=US-ASCII")
      request.addHeader("Content-Type", "application/xml")
      request.setBody(body("HELLO WORLD")) // content type is not overwritten here as its already set before
      val req = request.buildRequest()
      req.getHeaders.get("Content-Type") must be_==("application/json; charset=US-ASCII")
    }

    "Set Realm.UsePreemptiveAuth to false when WSAuthScheme.DIGEST being used" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.DIGEST)
      val req = request.buildRequest()
      req.getRealm.isUsePreemptiveAuth must beFalse
    }

    "Set Realm.UsePreemptiveAuth to true when WSAuthScheme.DIGEST not being used" in {
      val client  = mock[StandaloneAhcWSClient]
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.BASIC)
      val req = request.buildRequest()
      req.getRealm.isUsePreemptiveAuth must beTrue
    }

    "For HTTP Headers" in {

      "add a new header" in {
        val client  = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        request
          .addHeader("header1", "value1")
          .buildRequest()
          .getHeaders
          .get("header1") must beEqualTo("value1")
      }

      "add new value for existing header" in {
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
        val client  = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        request
          .setHeaders(Map("header1" -> Seq("value1").asJava, "header2" -> Seq("value2").asJava).asJava)
          .buildRequest()
          .getHeaders
          .get("header1") must beEqualTo("value1")
      }

      "keep existing headers when adding a new one" in {
        val client  = mock[StandaloneAhcWSClient]
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

      "get a single header" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        request.getHeader("header1").asScala must beSome("value1")
      }

      "get an empty optional when header is not present" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        request.getHeader("non").asScala must beNone
      }

      "get all values for a header" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        request.getHeaderValues("header1").asScala must containTheSameElementsAs(Seq("value1", "value2"))
      }

      "get an empty list when header is not present" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        request.getHeaderValues("Non").asScala must beEmpty
      }

      "get all headers" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "Value1ForHeader1")
          .addHeader("header1", "Value2ForHeader1")
          .addHeader("header2", "Value1ForHeader2")

        val headers = request.getHeaders.asScala
        headers("header1").asScala must containTheSameElementsAs(Seq("Value1ForHeader1", "Value2ForHeader1"))
        headers("header2").asScala must containTheSameElementsAs(Seq("Value1ForHeader2"))
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

      "deterministic query param order a" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .buildRequest()

        request.getUrl must contain("p1=v1&p2=v2")
      }

      "deterministic query param order b" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p2", "v2")
          .addQueryParameter("p1", "v1")
          .buildRequest()

        request.getUrl must contain("p2=v2&p1=v1")
      }

      "deterministic query param order for duplicate keys" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .addQueryParameter("p1", "v3")
          .addQueryParameter("p1", "v4")
          .buildRequest()

        request.getUrl must contain("p1=v1&p1=v3&p1=v4&p2=v2")
      }

      "add new value for existing parameter" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p1", "v2")
          .buildRequest()

        request.getUrl must contain("p1=v1")
        request.getUrl must contain("p1=v2")
      }

      "keep existing parameters when adding a new one" in {
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
          .setQueryString(Map("p1" -> Seq("v1").asJava, "p2" -> Seq("v2").asJava).asJava)
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

      "support a query string parameter with an encoded equals sign" in {
        import scala.collection.JavaConverters._
        val client      = mock[StandaloneAhcWSClient]
        val request     = new StandaloneAhcWSRequest(client, "http://example.com?bar=F%3Dma", /*materializer*/ null)
        val queryParams = request.buildRequest().getQueryParams.asScala
        val p           = queryParams(0)

        p.getName must beEqualTo("bar")
        p.getValue must beEqualTo("F%253Dma")
      }

      "not support a query string if it starts with = and is empty" in {
        requestWithQueryString("=&src=typd") must throwA[RuntimeException]
      }
    }

    "For Cookies" in {

      def cookie(name: String, value: String): WSCookie = {
        new WSCookieBuilder().setName(name).setValue(value).build()
      }

      "get existing cookies" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))

        val cookiesInRequest: mutable.Buffer[WSCookie] = request.getCookies.asScala
        cookiesInRequest.size must beEqualTo(1)
        val cookieInRequest: WSCookie = cookiesInRequest.head
        cookieInRequest.getName must beEqualTo("cookie1")
        cookieInRequest.getValue must beEqualTo("value1")
      }

      "add a new cookie" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .buildRequest()

        request.getCookies.asScala.head.name must beEqualTo("cookie1")
      }

      "add more than one cookie" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.name must beEqualTo("cookie1")
        request.getCookies.asScala(1).name must beEqualTo("cookie2")
      }

      "keep existing cookies when adding a new one" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .addCookie(cookie("cookie2", "value2"))
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.name must beEqualTo("cookie1")
        request.getCookies.asScala(1).name must beEqualTo("cookie2")
      }

      "set all cookies" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .setCookies(List(cookie("cookie1", "value1"), cookie("cookie2", "value2")).asJava)
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.name must beEqualTo("cookie1")
        request.getCookies.asScala(1).name must beEqualTo("cookie2")
      }

      "discard old cookies when setting" in {
        val client = mock[StandaloneAhcWSClient]
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .setCookies(List(cookie("cookie3", "value1"), cookie("cookie4", "value2")).asJava)
          .buildRequest()

        request.getCookies.asScala must size(2)
        request.getCookies.asScala.head.name must beEqualTo("cookie3")
        request.getCookies.asScala(1).name must beEqualTo("cookie4")
      }
    }
  }

  def requestWithTimeout(timeout: Duration) = {
    val client  = mock[StandaloneAhcWSClient]
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setRequestTimeout(timeout)
    request.buildRequest().getRequestTimeout()
  }

  def requestWithQueryString(query: String) = {
    import scala.collection.JavaConverters._
    val client  = mock[StandaloneAhcWSClient]
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setQueryString(query)
    val queryParams = request.buildRequest().getQueryParams
    queryParams.asScala
  }
}
