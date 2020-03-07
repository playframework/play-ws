/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import org.specs2.execute.Result
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator
import play.shaded.ahc.org.asynchttpclient.Param
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyType
import play.shaded.ahc.org.asynchttpclient.{ Request => AHCRequest }

class AhcWSRequestSpec
    extends Specification
    with Mockito
    with AfterAll
    with DefaultBodyReadables
    with DefaultBodyWritables {

  sequential

  implicit val system       = ActorSystem()
  implicit val materializer = Materializer.matFromSystem
  val wsClient              = StandaloneAhcWSClient()

  override def afterAll: Unit = {
    wsClient.close()
    system.terminate()
  }

  def withClient(block: StandaloneWSClient => Result): Result = {
    block(wsClient)
  }

  "Given the full URL" in {

    implicit val materializer = mock[akka.stream.Materializer]
    val client                = mock[StandaloneAhcWSClient]

    "request withQueryStringParameters" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      request.uri.toString must equalTo("http://example.com")
      request.withQueryStringParameters("bar" -> "baz").uri.toString must equalTo("http://example.com?bar=baz")
      request.withQueryStringParameters("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo(
        "http://example.com?bar=bah&bar=baz"
      )
    }

    "correctly URL-encode the query string part" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      request.withQueryStringParameters("&" -> "=").uri.toString must equalTo("http://example.com?%26=%3D")
    }

    "set all query string parameters" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      request.withQueryStringParameters("bar" -> "baz").uri.toString must equalTo("http://example.com?bar=baz")
      request.withQueryStringParameters("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo(
        "http://example.com?bar=bah&bar=baz"
      )
    }

    "discard old query parameters when setting new ones" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      request
        .withQueryStringParameters("bar" -> "baz")
        .withQueryStringParameters("bar" -> "bah")
        .uri
        .toString must equalTo("http://example.com?bar=bah")
    }

    "add query string param" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      request
        .withQueryStringParameters("bar" -> "baz")
        .addQueryStringParameters("bar" -> "bah")
        .uri
        .toString must equalTo("http://example.com?bar=bah&bar=baz")
    }

    "support adding several query string values for a parameter" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")
      val newRequest = request
        .withQueryStringParameters("play" -> "foo1")
        .addQueryStringParameters("play" -> "foo2")

      newRequest.queryString.get("play") must beSome.which(_.contains("foo1"))
      newRequest.queryString.get("play") must beSome.which(_.contains("foo2"))
      newRequest.queryString.get("play") must beSome.which(_.size == 2)
    }

    "support several query string values for  a parameter" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withQueryStringParameters("foo" -> "foo1", "foo" -> "foo2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        val paramsList: Seq[Param] = req.getQueryParams.asScala.toSeq
        paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo1")) must beTrue
        paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo2")) must beTrue
        paramsList.count(p => p.getName == "foo") must beEqualTo(2)
      }

    }

  }

  "For Cookies" in {

    def cookie(name: String, value: String): WSCookie = DefaultWSCookie(name, value)

    "add cookies to request" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .addCookies(cookie("cookie1", "value1"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getCookies.asScala must size(1)
        req.getCookies.asScala.head.name must beEqualTo("cookie1")
        req.getCookies.asScala.head.value must beEqualTo("value1")
      }
    }

    "set all cookies for request" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getCookies.asScala must size(2)
        req.getCookies.asScala.head.name must beEqualTo("cookie1")
        req.getCookies.asScala.head.value must beEqualTo("value1")

        req.getCookies.asScala(1).name must beEqualTo("cookie2")
        req.getCookies.asScala(1).value must beEqualTo("value2")
      }
    }

    "keep old cookies when adding a new one" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"))
          .addCookies(cookie("cookie2", "value2"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getCookies.asScala must size(2)
        req.getCookies.asScala.head.name must beEqualTo("cookie1")
        req.getCookies.asScala.head.value must beEqualTo("value1")

        req.getCookies.asScala(1).name must beEqualTo("cookie2")
        req.getCookies.asScala(1).value must beEqualTo("value2")
      }
    }

    "discard all cookies when setting new ones" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .withCookies(cookie("cookie3", "value3"), cookie("cookie4", "value4"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getCookies.asScala must size(2)
        req.getCookies.asScala.head.name must beEqualTo("cookie3")
        req.getCookies.asScala.head.value must beEqualTo("value3")

        req.getCookies.asScala(1).name must beEqualTo("cookie4")
        req.getCookies.asScala(1).value must beEqualTo("value4")
      }
    }

    "set cookies through Cookie header directly" in {
      withClient { client =>
        val cookies = Seq("cookie1" -> "value1", "cookie2" -> "value2")
        val req: AHCRequest = client
          .url("http://example.com")
          .addHttpHeaders("Cookie" -> cookies.map(c => c._1 + "=" + c._2).mkString(", "))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        req.getHeaders.entries.asScala must size(1)
        req.getHeaders.get("Cookie") must beEqualTo("cookie1=value1, cookie2=value2")
      }
    }
  }

  "For HTTP Headers" in {

    "support setting headers" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1", "key" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.getAll("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

    "discard old headers when setting" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key1" -> "value1")
          .withHttpHeaders("key2" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.get("key1") must beNull
        req.getHeaders.get("key2") must beEqualTo("value2")
      }
    }

    "support adding headers" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1")
          .addHttpHeaders("key" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.getAll("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

    "keep existing headers when adding a new one" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key1" -> "value1")
          .addHttpHeaders("key2" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.get("key1") must beEqualTo("value1")
        req.getHeaders.get("key2") must beEqualTo("value2")
      }
    }

    "not make Content-Type header if there is Content-Type in headers already" in {
      withClient { client =>
        import scala.collection.JavaConverters._

        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("content-type" -> "fake/contenttype; charset=utf-8")
          .withBody("I am a text/plain body")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.getAll(HttpHeaderNames.CONTENT_TYPE.toString()).asScala must_== Seq(
          "fake/contenttype; charset=utf-8"
        )
      }
    }

    "treat headers as case insensitive" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1", "KEY" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.getAll("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
      }
    }

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
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withBody(Map("param1" -> Seq("value1")))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        (new String(req.getByteData, "UTF-8")) must_== ("param1=value1")
      }
    }

    "Have form params for content type application/x-www-form-urlencoded when signed" in {
      withClient { client =>
        import scala.collection.JavaConverters._
        val consumerKey  = ConsumerKey("key", "secret")
        val requestToken = RequestToken("token", "secret")
        val calc         = OAuthCalculator(consumerKey, requestToken)
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withBody(Map("param1" -> Seq("value1")))
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        // Note we use getFormParams instead of getByteData here.
        req.getFormParams.asScala must containTheSameElementsAs(
          List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1"))
        )
        req.getByteData must beNull // should NOT result in byte data.

        val headers = req.getHeaders
        headers.get("Content-Length") must beNull
      }
    }

    "Parse no params for empty params map" in {
      withClient { client =>
        val consumerKey  = ConsumerKey("key", "secret")
        val requestToken = RequestToken("token", "secret")
        val calc         = OAuthCalculator(consumerKey, requestToken)
        val reqEmptyParams: AHCRequest = client
          .url("http://playframework.com/")
          .withBody(Map.empty[String, Seq[String]])
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        reqEmptyParams.getFormParams.asScala must beEmpty
      }
    }

    "Have form body for content type text/plain" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders(HttpHeaderNames.CONTENT_TYPE.toString() -> "text/plain")
          .withBody("HELLO WORLD")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        (new String(req.getByteData, "UTF-8")) must be_==("HELLO WORLD")
        val headers = req.getHeaders
        headers.get("Content-Length") must beNull
      }
    }

    "Have form body for content type application/x-www-form-urlencoded explicitly set" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders(
            // set content type by hand
            HttpHeaderNames.CONTENT_TYPE.toString() -> "application/x-www-form-urlencoded"
          )
          .withBody("HELLO WORLD") // and body is set to string (see #5221)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        (new String(req.getByteData, "UTF-8")) must be_==("HELLO WORLD") // should result in byte data.
      }
    }

    "Send binary data as is" in withClient { client =>
      val binData = ByteString((0 to 511).map(_.toByte).toArray)
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .addHttpHeaders(HttpHeaderNames.CONTENT_TYPE.toString() -> "application/x-custom-bin-data")
        .withBody(binData)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()

      ByteString(req.getByteData) must_== binData
    }

    "Preserve existing headers when setting the body" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("Some-Header" -> "Some-Value")
          .withBody("HELLO WORLD") // will set content-type header
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        req.getHeaders.get("Some-Header") must beEqualTo("Some-Value")
      }
    }
  }

  "When using a Proxy Server" in {

    "support a proxy server with basic" in withClient { client =>
      val proxy = DefaultWSProxyServer(
        protocol = Some("https"),
        host = "localhost",
        port = 8080,
        proxyType = Some("socksv5"),
        principal = Some("principal"),
        password = Some("password"),
        nonProxyHosts = Some(List("derp"))
      )
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withProxyServer(proxy)
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

    "support a proxy server with NTLM" in withClient { client =>
      val proxy = DefaultWSProxyServer(
        protocol = Some("ntlm"),
        host = "localhost",
        port = 8080,
        principal = Some("principal"),
        password = Some("password"),
        ntlmDomain = Some("somentlmdomain")
      )
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withProxyServer(proxy)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      val actual = req.getProxyServer

      (actual.getHost must be).equalTo("localhost")
      (actual.getPort must be).equalTo(8080)
      (actual.getRealm.getPrincipal must be).equalTo("principal")
      (actual.getRealm.getPassword must be).equalTo("password")
      (actual.getRealm.getNtlmDomain must be).equalTo("somentlmdomain")
      (actual.getRealm.getScheme must be).equalTo(AuthScheme.NTLM)
    }

    "support a proxy server" in withClient { client =>
      val proxy = DefaultWSProxyServer(host = "localhost", port = 8080)
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withProxyServer(proxy)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      val actual = req.getProxyServer

      (actual.getHost must be).equalTo("localhost")
      (actual.getPort must be).equalTo(8080)
      actual.getRealm must beNull
    }
  }

  "StandaloneAhcWSRequest supports" in {

    "replace url" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withUrl("http://www.example.com/")
      req.url must_=== "http://www.example.com/"
    }

    "a custom signature calculator" in {
      var called = false
      val calc = new SignatureCalculator with WSSignatureCalculator {
        override def calculateAndAddSignature(
            request: play.shaded.ahc.org.asynchttpclient.Request,
            requestBuilder: play.shaded.ahc.org.asynchttpclient.RequestBuilderBase[_]
        ): Unit = {
          called = true
        }
      }
      withClient { client =>
        client
          .url("http://playframework.com/")
          .sign(calc)
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        called must beTrue
      }
    }

    "a virtual host" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withVirtualHost("192.168.1.1")
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      (req.getVirtualHost must be).equalTo("192.168.1.1")
    }

    "follow redirects" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withFollowRedirects(follow = true)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      req.getFollowRedirect must beEqualTo(true)
    }

    "finite timeout" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withRequestTimeout(1000.millis)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      (req.getRequestTimeout must be).equalTo(1000)
    }

    "infinite timeout" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withRequestTimeout(Duration.Inf)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      (req.getRequestTimeout must be).equalTo(-1)
    }

    "no negative timeout" in withClient { client =>
      client.url("http://playframework.com/").withRequestTimeout(-1.millis) should throwAn[IllegalArgumentException]
    }

    "no timeout greater than Int.MaxValue" in withClient { client =>
      client
        .url("http://playframework.com/")
        .withRequestTimeout((Int.MaxValue.toLong + 1).millis) should throwAn[IllegalArgumentException]
    }
  }

  "Set Realm.UsePreemptiveAuth" in {
    "to false when WSAuthScheme.DIGEST being used" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withAuth("usr", "pwd", WSAuthScheme.DIGEST)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      req.getRealm.isUsePreemptiveAuth must beFalse
    }

    "to true when WSAuthScheme.DIGEST not being used" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withAuth("usr", "pwd", WSAuthScheme.BASIC)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      req.getRealm.isUsePreemptiveAuth must beTrue
    }
  }

  "Not remove a user defined content length header" in withClient { client =>
    val req: AHCRequest = client
      .url("http://playframework.com/")
      .withBody(Map("param1" -> Seq("value1")))
      .withHttpHeaders("Content-Length" -> "9001") // add a meaningless content length here...
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    (new String(req.getByteData, "UTF-8")) must be_==("param1=value1") // should result in byte data.

    val headers = req.getHeaders
    headers.get("Content-Length") must_== ("9001")
  }

  "Remove a user defined content length header if we are parsing body explicitly when signed" in withClient { client =>
    import scala.collection.JavaConverters._
    val consumerKey  = ConsumerKey("key", "secret")
    val requestToken = RequestToken("token", "secret")
    val calc         = OAuthCalculator(consumerKey, requestToken)
    val req: AHCRequest = client
      .url("http://playframework.com/")
      .withBody(Map("param1" -> Seq("value1")))
      .withHttpHeaders("Content-Length" -> "9001") // add a meaningless content length here...
      .sign(calc) // this is signed, so content length is no longer valid per #5221
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    val headers = req.getHeaders
    req.getByteData must beNull // should NOT result in byte data.
    req.getFormParams.asScala must containTheSameElementsAs(
      List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1"))
    )
    headers.get("Content-Length") must beNull // no content length!
  }

  "Verify Content-Type header is passed through correctly" in withClient { client =>
    import scala.collection.JavaConverters._
    val req: AHCRequest = client
      .url("http://playframework.com/")
      .withHttpHeaders(HttpHeaderNames.CONTENT_TYPE.toString() -> "text/plain; charset=US-ASCII")
      .withBody("HELLO WORLD")
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getHeaders.getAll(HttpHeaderNames.CONTENT_TYPE.toString()).asScala must_== Seq("text/plain; charset=US-ASCII")
  }

}
