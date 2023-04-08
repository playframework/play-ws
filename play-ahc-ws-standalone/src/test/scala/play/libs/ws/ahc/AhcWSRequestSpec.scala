/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import com.typesafe.config.ConfigFactory

import java.time.Duration
import java.util.Collections

import org.scalatest.wordspec.AnyWordSpec
import play.libs.oauth.OAuth
import play.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames
import play.shaded.ahc.org.asynchttpclient.Request
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.jdk.OptionConverters._

class AhcWSRequestSpec extends AnyWordSpec with DefaultBodyReadables with DefaultBodyWritables {

  "AhcWSRequest" should {

    "Have GET method as the default" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      assert(request.getMethod == "GET")
      assert(request.buildRequest().getMethod == "GET")
    }

    "Set virtualHost appropriately" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setVirtualHost("foo.com")
      val actual = request.buildRequest().getVirtualHost()
      assert(actual == "foo.com")
    }

    "set the url" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
      assert(req.getUrl == "http://playframework.com/")
      val setReq = req.setUrl("http://example.com")
      assert(setReq.getUrl == "http://example.com")
      assert(setReq == req)
    }

    "For POST requests" should {

      "get method" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setMethod("POST")

        assert(req.getMethod == "POST")
      }

      "set text/plain content-types for text bodies" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body("HELLO WORLD"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        assert(req.getStringData == "HELLO WORLD")
      }

      "sets content type based on a body when its not explicitly set" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body("HELLO WORLD")) // set body with a content type
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        assert(req.getHeaders.get(HttpHeaderNames.CONTENT_TYPE) == "text/plain; charset=UTF-8")
        assert(req.getStringData == "HELLO WORLD")
      }

      "keep existing content type when setting body" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setContentType("text/plain+hello") // set content type by hand
          .setBody(body("HELLO WORLD"))       // and body is set to string (see #5221)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        // preserve the content type
        assert(req.getHeaders.get(HttpHeaderNames.CONTENT_TYPE) == "text/plain+hello; charset=UTF-8")
        // should result in byte data.
        assert(req.getStringData == "HELLO WORLD")
      }

      "have form params when passing in map" in {
        import scala.jdk.CollectionConverters._
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val req = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
          .setBody(body(Collections.singletonMap("param1", "value1")))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        // Must set the form url encoding autoomatically.
        assert(req.getHeaders.get("Content-Type") == "application/x-www-form-urlencoded")

        // Note we use getFormParams instead of getByteData here.
        assert(
          req.getFormParams.asScala.toSet == Set(
            new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")
          )
        )
      }

      "have form params when content-type application/x-www-form-urlencoded and signed" in {
        import scala.jdk.CollectionConverters._
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
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
        assert(
          req.getFormParams.asScala.toSet == Set(
            new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")
          )
        )
      }

      "remove a user defined content length header if we are parsing body explicitly when signed" in {
        import scala.jdk.CollectionConverters._
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
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
        assert(
          req.getFormParams.asScala.toSet == Set(
            new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")
          )
        )
        assert(headers.get("Content-Length") == null) // no content length!
      }

    }

    "Use a custom signature calculator" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      var called = false
      val calc = new SignatureCalculator with WSSignatureCalculator {
        override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
          called = true
        }
      }
      new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
        .sign(calc)
        .buildRequest()
      assert(called)
    }

    "setRequestTimeout(java.time.Duration)" should {

      "support setting a request timeout to a duration" in {
        assert(requestWithTimeout(Duration.ofSeconds(1)) == 1000)
      }

      "support setting a request timeout duration to infinite using -1" in {
        assert(requestWithTimeout(Duration.ofMillis(-1)) == -1)
      }

      "support setting a request timeout duration to infinite using any negative duration" in {
        assert(requestWithTimeout(Duration.ofMillis(-2)) == -1)
        assert(requestWithTimeout(Duration.ofMillis(-15)) == -1)
        assert(requestWithTimeout(Duration.ofSeconds(-1)) == -1)
        assert(requestWithTimeout(Duration.ofMillis(java.lang.Integer.MIN_VALUE)) == -1)
      }

      "support setting a request timeout duration to Long.MAX_VALUE as infinite" in {
        assert(requestWithTimeout(Duration.ofMillis(java.lang.Long.MAX_VALUE)) == -1)
      }

      "not support setting a request timeout to null" in {
        assertThrows[IllegalArgumentException] { requestWithTimeout(null) }
      }
    }

    "allow adding an explicit Content-Type header if the BodyWritable doesn't set the Content-Type" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody(body("HELLO WORLD", null))            // content type is not set
      request.addHeader("Content-Type", "application/json") // will be used as content type is not set with a body
      val req = request.buildRequest()
      assert(req.getHeaders.get("Content-Type") == "application/json")
    }

    "ignore explicit Content-Type header if the BodyWritable already set the Content-Type" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setBody(body("HELLO WORLD"))
      request.addHeader("Content-Type", "application/json") // will be ignored since body already sets content type
      val req = request.buildRequest()
      assert(req.getHeaders.get("Content-Type") == "text/plain; charset=UTF-8")
    }

    "only send first Content-Type header and keep the charset when setting the Content-Type multiple times" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.addHeader("Content-Type", "application/json; charset=US-ASCII")
      request.addHeader("Content-Type", "application/xml")
      request.setBody(body("HELLO WORLD")) // content type is not overwritten here as its already set before
      val req = request.buildRequest()
      assert(req.getHeaders.get("Content-Type") == "application/json; charset=US-ASCII")
    }

    "Set Realm.UsePreemptiveAuth to false when WSAuthScheme.DIGEST being used" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.DIGEST)
      val req = request.buildRequest()
      assert(req.getRealm.isUsePreemptiveAuth == false)
    }

    "Set Realm.UsePreemptiveAuth to true when WSAuthScheme.DIGEST not being used" in {
      val client = StandaloneAhcWSClient.create(
        AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
      )
      val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
      request.setAuth("usr", "pwd", WSAuthScheme.BASIC)
      val req = request.buildRequest()
      assert(req.getRealm.isUsePreemptiveAuth)
    }

    "For HTTP Headers" should {

      "add a new header" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        assert(
          request
            .addHeader("header1", "value1")
            .buildRequest()
            .getHeaders
            .get("header1") == "value1"
        )
      }

      "add new value for existing header" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        assert(request.getHeaders.get("header1").asScala.toSet == Set("value1", "value2"))

        val ahcRequest = request.buildRequest()

        assert(ahcRequest.getHeaders.getAll("header1").asScala.toSet == Set("value1", "value2"))
      }

      "set all headers" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        assert(
          request
            .setHeaders(Map("header1" -> Seq("value1").asJava, "header2" -> Seq("value2").asJava).asJava)
            .buildRequest()
            .getHeaders
            .get("header1") == "value1"
        )
      }

      "keep existing headers when adding a new one" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)

        val ahcReq = request
          .addHeader("header1", "value1")
          .addHeader("header2", "value2")
          .buildRequest()

        assert(ahcReq.getHeaders.get("header1") == "value1")
        assert(ahcReq.getHeaders.get("header2") == "value2")
      }

      "treat header names case insensitively" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("HEADER1", "value2")
          .buildRequest()

        assert(request.getHeaders.getAll("header1").asScala.toSet == Set("value1", "value2"))
      }

      "get a single header" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        assert(request.getHeader("header1").toScala == Some("value1"))
      }

      "get an empty optional when header is not present" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        assert(request.getHeader("non").toScala.isEmpty)
      }

      "get all values for a header" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        assert(request.getHeaderValues("header1").asScala.toSet == Set("value1", "value2"))
      }

      "get an empty list when header is not present" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "value1")
          .addHeader("header1", "value2")

        assert(request.getHeaderValues("Non").asScala.isEmpty)
      }

      "get all headers" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addHeader("header1", "Value1ForHeader1")
          .addHeader("header1", "Value2ForHeader1")
          .addHeader("header2", "Value1ForHeader2")

        val headers = request.getHeaders.asScala
        assert(headers("header1").asScala.toSet == Set("Value1ForHeader1", "Value2ForHeader1"))
        assert(headers("header2").asScala.toSet == Set("Value1ForHeader2"))
      }

    }

    "For query string parameters" should {

      "add query string parameter" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .buildRequest()

        assert(request.getUrl.contains("p1=v1"))
      }

      "deterministic query param order a" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .buildRequest()

        assert(request.getUrl.contains("p1=v1&p2=v2"))
      }

      "deterministic query param order b" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p2", "v2")
          .addQueryParameter("p1", "v1")
          .buildRequest()

        assert(request.getUrl.contains("p2=v2&p1=v1"))
      }

      "deterministic query param order for duplicate keys" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .addQueryParameter("p1", "v3")
          .addQueryParameter("p1", "v4")
          .buildRequest()

        assert(request.getUrl.contains("p1=v1&p1=v3&p1=v4&p2=v2"))
      }

      "add new value for existing parameter" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p1", "v2")
          .buildRequest()

        assert(request.getUrl.contains("p1=v1"))
        assert(request.getUrl.contains("p1=v2"))
      }

      "keep existing parameters when adding a new one" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("p1", "v1")
          .addQueryParameter("p2", "v2")
          .buildRequest()

        assert(request.getUrl.contains("p1=v1"))
        assert(request.getUrl.contains("p2=v2"))
      }

      "set all the parameters" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .setQueryString(Map("p1" -> Seq("v1").asJava, "p2" -> Seq("v2").asJava).asJava)
          .buildRequest()

        assert(request.getUrl.contains("p1=v1"))
        assert(request.getUrl.contains("p2=v2"))
      }

      "set a query string appropriately" in {
        val queryParams = requestWithQueryString("q=playframework&src=typd")
        assert(queryParams.size == 2)
        assert(queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")))
        assert(queryParams.exists(p => (p.getName == "src") && (p.getValue == "typd")))
      }

      "support several query string values for a parameter" in {
        val queryParams = requestWithQueryString("q=scala&q=playframework&q=fp")
        assert(queryParams.size == 3)
        assert(queryParams.exists(p => (p.getName == "q") && (p.getValue == "scala")))
        assert(queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")))
        assert(queryParams.exists(p => (p.getName == "q") && (p.getValue == "fp")))
        assert(queryParams.count(p => p.getName == "q") == 3)
      }

      "support a query string parameter without value" in {
        val queryParams = requestWithQueryString("q=playframework&src=")
        assert(queryParams.size == 2)
        assert(queryParams.exists(p => (p.getName == "q") && (p.getValue == "playframework")))
        assert(queryParams.exists(p => (p.getName.equals("src")) && (p.getValue == null)))
      }

      "not support a query string with more than 2 = per part" in {
        assertThrows[RuntimeException] { requestWithQueryString("q=scala=playframework&src=typd") }
      }

      "support a query string parameter with an encoded equals sign" in {
        import scala.jdk.CollectionConverters._
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request     = new StandaloneAhcWSRequest(client, "http://example.com?bar=F%3Dma", /*materializer*/ null)
        val queryParams = request.buildRequest().getQueryParams.asScala
        val p           = queryParams(0)

        assert(p.getName == "bar")
        assert(p.getValue == "F%253Dma")
      }

      "not support a query string if it starts with = and is empty" in {
        assertThrows[RuntimeException] { requestWithQueryString("=&src=typd") }
      }

      "enable url encoding by default" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("abc+def", "uvw+xyz")
          .buildRequest()

        assert(request.getUrl == "http://example.com?abc%2Bdef=uvw%2Bxyz")
      }

      "disable url encoding globally via client config" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory
            .forConfig(ConfigFactory.load(), this.getClass.getClassLoader)
            .copy(disableUrlEncoding = true), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("abc+def", "uvw+xyz")
          .buildRequest()

        assert(request.getUrl == "http://example.com?abc+def=uvw+xyz")
      }

      "disable url encoding for specific request only" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addQueryParameter("abc+def", "uvw+xyz")
          .setDisableUrlEncoding(true)
          .buildRequest()

        assert(request.getUrl == "http://example.com?abc+def=uvw+xyz")
      }
    }

    "For Cookies" should {

      def cookie(name: String, value: String): WSCookie = {
        new WSCookieBuilder().setName(name).setValue(value).build()
      }

      "get existing cookies" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))

        val cookiesInRequest: mutable.Buffer[WSCookie] = request.getCookies.asScala
        assert(cookiesInRequest.size == 1)
        val cookieInRequest: WSCookie = cookiesInRequest.head
        assert(cookieInRequest.getName == "cookie1")
        assert(cookieInRequest.getValue == "value1")
      }

      "add a new cookie" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .buildRequest()

        assert(request.getCookies.asScala.head.name == "cookie1")
      }

      "add more than one cookie" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .buildRequest()

        assert(request.getCookies.asScala.size == 2)
        assert(request.getCookies.asScala.head.name == "cookie1")
        assert(request.getCookies.asScala(1).name == "cookie2")
      }

      "keep existing cookies when adding a new one" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookie(cookie("cookie1", "value1"))
          .addCookie(cookie("cookie2", "value2"))
          .buildRequest()

        assert(request.getCookies.asScala.size == 2)
        assert(request.getCookies.asScala.head.name == "cookie1")
        assert(request.getCookies.asScala(1).name == "cookie2")
      }

      "set all cookies" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .setCookies(List(cookie("cookie1", "value1"), cookie("cookie2", "value2")).asJava)
          .buildRequest()

        assert(request.getCookies.asScala.size == 2)
        assert(request.getCookies.asScala.head.name == "cookie1")
        assert(request.getCookies.asScala(1).name == "cookie2")
      }

      "discard old cookies when setting" in {
        val client = StandaloneAhcWSClient.create(
          AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
        )
        val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
          .addCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .setCookies(List(cookie("cookie3", "value1"), cookie("cookie4", "value2")).asJava)
          .buildRequest()

        assert(request.getCookies.asScala.size == 2)
        assert(request.getCookies.asScala.head.name == "cookie3")
        assert(request.getCookies.asScala(1).name == "cookie4")
      }
    }
  }

  def requestWithTimeout(timeout: Duration) = {
    val client = StandaloneAhcWSClient.create(
      AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
    )
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setRequestTimeout(timeout)
    request.buildRequest().getRequestTimeout()
  }

  def requestWithQueryString(query: String) = {
    import scala.jdk.CollectionConverters._
    val client = StandaloneAhcWSClient.create(
      AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass.getClassLoader), /*materializer*/ null
    )
    val request = new StandaloneAhcWSRequest(client, "http://example.com", /*materializer*/ null)
    request.setQueryString(query)
    val queryParams = request.buildRequest().getQueryParams
    queryParams.asScala
  }
}
