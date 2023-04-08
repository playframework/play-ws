/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec

import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator
import play.shaded.ahc.org.asynchttpclient.Param
import play.shaded.ahc.org.asynchttpclient.{ Request => AHCRequest }

import scala.reflect.ClassTag

class AhcWSRequestSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with DefaultBodyReadables
    with DefaultBodyWritables
    with OptionValues {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  implicit val system: ActorSystem        = ActorSystem()
  implicit val materializer: Materializer = Materializer.matFromSystem
  val wsClient                            = StandaloneAhcWSClient()

  override def afterAll(): Unit = {
    wsClient.close()
    system.terminate()
  }

  def withClient[A](block: StandaloneWSClient => A): A = {
    block(wsClient)
  }

  "Given the full URL" should {

    implicit val materializer: Materializer = mock[org.apache.pekko.stream.Materializer]
    val client                              = mock[StandaloneAhcWSClient]

    "request withQueryStringParameters" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      assert(request.uri.toString == "http://example.com")
      assert(request.withQueryStringParameters("bar" -> "baz").uri.toString == "http://example.com?bar=baz")
      assert(
        request
          .withQueryStringParameters("bar" -> "baz", "bar" -> "bah")
          .uri
          .toString == "http://example.com?bar=bah&bar=baz"
      )
    }

    "correctly URL-encode the query string part" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      assert(request.withQueryStringParameters("&" -> "=").uri.toString == "http://example.com?%26=%3D")
    }

    "set all query string parameters" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      assert(request.withQueryStringParameters("bar" -> "baz").uri.toString == "http://example.com?bar=baz")
      assert(
        request
          .withQueryStringParameters("bar" -> "baz", "bar" -> "bah")
          .uri
          .toString == "http://example.com?bar=bah&bar=baz"
      )
    }

    "discard old query parameters when setting new ones" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      assert(
        request
          .withQueryStringParameters("bar" -> "baz")
          .withQueryStringParameters("bar" -> "bah")
          .uri
          .toString == "http://example.com?bar=bah"
      )
    }

    "add query string param" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")

      assert(
        request
          .withQueryStringParameters("bar" -> "baz")
          .addQueryStringParameters("bar" -> "bah")
          .uri
          .toString == "http://example.com?bar=bah&bar=baz"
      )
    }

    "support adding several query string values for a parameter" in {
      val request = StandaloneAhcWSRequest(client, "http://example.com")
      val newRequest = request
        .withQueryStringParameters("play" -> "foo1")
        .addQueryStringParameters("play" -> "foo2")

      val actual = newRequest.queryString.get("play").value
      assert(actual.contains("foo1"))
      assert(actual.contains("foo2"))
      assert(actual.size == 2)
    }

    "support several query string values for  a parameter" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withQueryStringParameters("foo" -> "foo1", "foo" -> "foo2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        val paramsList: Seq[Param] = req.getQueryParams.asScala.toSeq
        assert(paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo1")))
        assert(paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo2")))
        assert(paramsList.count(p => p.getName == "foo") == 2)
      }

    }

  }

  "For Cookies" should {

    def cookie(name: String, value: String): WSCookie = DefaultWSCookie(name, value)

    "add cookies to request" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .addCookies(cookie("cookie1", "value1"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        assert(req.getCookies.asScala.size == 1)
        assert(req.getCookies.asScala.head.name == "cookie1")
        assert(req.getCookies.asScala.head.value == "value1")
      }
    }

    "set all cookies for request" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://example.com")
          .withCookies(cookie("cookie1", "value1"), cookie("cookie2", "value2"))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()

        assert(req.getCookies.asScala.size == 2)
        assert(req.getCookies.asScala.head.name == "cookie1")
        assert(req.getCookies.asScala.head.value == "value1")

        assert(req.getCookies.asScala(1).name == "cookie2")
        assert(req.getCookies.asScala(1).value == "value2")
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

        assert(req.getCookies.asScala.size == 2)
        assert(req.getCookies.asScala.head.name == "cookie1")
        assert(req.getCookies.asScala.head.value == "value1")

        assert(req.getCookies.asScala(1).name == "cookie2")
        assert(req.getCookies.asScala(1).value == "value2")
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

        assert(req.getCookies.asScala.size == 2)
        assert(req.getCookies.asScala.head.name == "cookie3")
        assert(req.getCookies.asScala.head.value == "value3")

        assert(req.getCookies.asScala(1).name == "cookie4")
        assert(req.getCookies.asScala(1).value == "value4")
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

        assert(req.getHeaders.entries.asScala.size == 1)
        assert(req.getHeaders.get("Cookie") == "cookie1=value1, cookie2=value2")
      }
    }
  }

  "For HTTP Headers" should {

    "support setting headers" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("key" -> "value1", "key" -> "value2")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        assert(req.getHeaders.getAll("key").asScala.toSet == Set("value1", "value2"))
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
        assert(req.getHeaders.get("key1") == null)
        assert(req.getHeaders.get("key2") == "value2")
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
        assert(req.getHeaders.getAll("key").asScala.toSet == Set("value1", "value2"))
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
        assert(req.getHeaders.get("key1") == "value1")
        assert(req.getHeaders.get("key2") == "value2")
      }
    }

    "not make Content-Type header if there is Content-Type in headers already" in {
      withClient { client =>
        import scala.jdk.CollectionConverters._

        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("content-type" -> "fake/contenttype; charset=utf-8")
          .withBody("I am a text/plain body")
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        assert(
          req.getHeaders.getAll(HttpHeaderNames.CONTENT_TYPE.toString()).asScala == Seq(
            "fake/contenttype; charset=utf-8"
          )
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
        assert(req.getHeaders.getAll("key").asScala.toSet == Set("value1", "value2"))
      }
    }

    "get a single header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key2" -> "value2")

        assert(req.header("Key1") == Some("value1"))
      }
    }

    "get all values for a header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        assert(req.headerValues("Key1").toSet == Set("value1", "value2"))
      }
    }

    "get none when header is not present" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        assert(req.header("Non").isEmpty)
      }
    }

    "get an empty seq when header has no values" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "some")

        assert(req.headerValues("Non").isEmpty)
      }
    }

    "get all the header" in {
      withClient { client =>
        val req = client
          .url("http://playframework.com/")
          .withHttpHeaders("Key1" -> "value1", "Key1" -> "value2", "Key2" -> "value")

        assert(req.headers("Key1").toSet == Set("value1", "value2"))
        assert(req.headers("Key2").toSet == Set("value"))
      }
    }
  }

  "For requests with body" should {

    "Have form params for content type application/x-www-form-urlencoded" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withBody(Map("param1" -> Seq("value1")))
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        assert((new String(req.getByteData, "UTF-8")) == "param1=value1")
      }
    }

    "Have form params for content type application/x-www-form-urlencoded when signed" in {
      withClient { client =>
        import scala.jdk.CollectionConverters._
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
        assert(
          req.getFormParams.asScala.toSet == List(
            new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")
          ).toSet
        )
        assert(req.getByteData == null) // should NOT result in byte data.

        val headers = req.getHeaders
        assert(headers.get("Content-Length") == null)
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

        assert(reqEmptyParams.getFormParams.asScala.isEmpty)
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

        assert((new String(req.getByteData, "UTF-8")) == "HELLO WORLD")
        val headers = req.getHeaders
        assert(headers.get("Content-Length") == null)
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
        assert((new String(req.getByteData, "UTF-8")) == "HELLO WORLD") // should result in byte data.
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

      assert(ByteString(req.getByteData) == binData)
    }

    "Preserve existing headers when setting the body" in {
      withClient { client =>
        val req: AHCRequest = client
          .url("http://playframework.com/")
          .withHttpHeaders("Some-Header" -> "Some-Value")
          .withBody("HELLO WORLD") // will set content-type header
          .asInstanceOf[StandaloneAhcWSRequest]
          .buildRequest()
        assert(req.getHeaders.get("Some-Header") == "Some-Value")
      }
    }
  }

  "When using a Proxy Server" should {

    "support a proxy server with basic" in withClient { client =>
      val proxy = DefaultWSProxyServer(
        protocol = Some("https"),
        host = "localhost",
        port = 8080,
        principal = Some("principal"),
        password = Some("password")
      )
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withProxyServer(proxy)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      val actual = req.getProxyServer

      assert(actual.getHost == "localhost")
      assert(actual.getPort == 8080)
      assert(actual.getRealm.getPrincipal == "principal")
      assert(actual.getRealm.getPassword == "password")
      assert(actual.getRealm.getScheme == AuthScheme.BASIC)
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

      assert(actual.getHost == "localhost")
      assert(actual.getPort == 8080)
      assert(actual.getRealm.getPrincipal == "principal")
      assert(actual.getRealm.getPassword == "password")
      assert(actual.getRealm.getNtlmDomain == "somentlmdomain")
      assert(actual.getRealm.getScheme == AuthScheme.NTLM)
    }

    "support a proxy server" in withClient { client =>
      val proxy = DefaultWSProxyServer(host = "localhost", port = 8080)
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withProxyServer(proxy)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      val actual = req.getProxyServer

      assert(actual.getHost == "localhost")
      assert(actual.getPort == 8080)
      assert(actual.getRealm == null)
    }
  }

  "StandaloneAhcWSRequest supports" should {

    "replace url" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withUrl("http://www.example.com/")
      assert(req.url == "http://www.example.com/")
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
        assert(called)
      }
    }

    "a virtual host" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withVirtualHost("192.168.1.1")
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getVirtualHost == "192.168.1.1")
    }

    "follow redirects" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withFollowRedirects(follow = true)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getFollowRedirect == true)
    }

    "enable url encoding by default" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .addQueryStringParameters("abc+def" -> "uvw+xyz")
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getUrl == "http://playframework.com/?abc%2Bdef=uvw%2Bxyz")
    }

    "disable url encoding globally via client config" in {
      val client = StandaloneAhcWSClient(AhcWSClientConfigFactory.forConfig().copy(disableUrlEncoding = true))
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .addQueryStringParameters("abc+def" -> "uvw+xyz")
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getUrl == "http://playframework.com/?abc+def=uvw+xyz")
    }

    "disable url encoding for specific request only" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .addQueryStringParameters("abc+def" -> "uvw+xyz")
        .withDisableUrlEncoding(disableUrlEncoding = true)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getUrl == "http://playframework.com/?abc+def=uvw+xyz")
    }

    "finite timeout" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withRequestTimeout(1000.millis)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getRequestTimeout == 1000)
    }

    "infinite timeout" in withClient { client =>
      val req: AHCRequest = client
        .url("http://playframework.com/")
        .withRequestTimeout(Duration.Inf)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getRequestTimeout == -1)
    }

    "no negative timeout" in withClient { client =>
      assertThrows[IllegalArgumentException] { client.url("http://playframework.com/").withRequestTimeout(-1.millis) }
    }

    "no timeout greater than Int.MaxValue" in withClient { client =>
      assertThrows[IllegalArgumentException] {
        client
          .url("http://playframework.com/")
          .withRequestTimeout((Int.MaxValue.toLong + 1).millis)
      }
    }
  }

  "Set Realm.UsePreemptiveAuth" should {
    "to false when WSAuthScheme.DIGEST being used" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withAuth("usr", "pwd", WSAuthScheme.DIGEST)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getRealm.isUsePreemptiveAuth == false)
    }

    "to true when WSAuthScheme.DIGEST not being used" in withClient { client =>
      val req = client
        .url("http://playframework.com/")
        .withAuth("usr", "pwd", WSAuthScheme.BASIC)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      assert(req.getRealm.isUsePreemptiveAuth)
    }
  }

  "Not remove a user defined content length header" in withClient { client =>
    val req: AHCRequest = client
      .url("http://playframework.com/")
      .withBody(Map("param1" -> Seq("value1")))
      .withHttpHeaders("Content-Length" -> "9001") // add a meaningless content length here...
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    assert((new String(req.getByteData, "UTF-8")) == "param1=value1") // should result in byte data.

    val headers = req.getHeaders
    assert(headers.get("Content-Length") == "9001")
  }

  "Remove a user defined content length header if we are parsing body explicitly when signed" in withClient { client =>
    import scala.jdk.CollectionConverters._
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
    assert(req.getByteData == null) // should NOT result in byte data.
    assert(
      req.getFormParams.asScala.toSet == List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")).toSet
    )
    assert(headers.get("Content-Length") == null) // no content length!
  }

  "Verify Content-Type header is passed through correctly" in withClient { client =>
    import scala.jdk.CollectionConverters._
    val req: AHCRequest = client
      .url("http://playframework.com/")
      .withHttpHeaders(HttpHeaderNames.CONTENT_TYPE.toString() -> "text/plain; charset=US-ASCII")
      .withBody("HELLO WORLD")
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    assert(
      req.getHeaders.getAll(HttpHeaderNames.CONTENT_TYPE.toString()).asScala == Seq("text/plain; charset=US-ASCII")
    )
  }

}
