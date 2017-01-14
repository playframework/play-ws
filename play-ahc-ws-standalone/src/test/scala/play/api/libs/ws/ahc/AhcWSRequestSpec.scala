/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.specs2.execute.Result
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.oauth.{ ConsumerKey, OAuthCalculator, RequestToken }
import play.api.libs.ws.{ StandaloneWSRequest, StandaloneWSResponse, _ }
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient.cookie.{ Cookie => AHCCookie }
import play.shaded.ahc.org.asynchttpclient.{ Param, Request => AHCRequest, Response => AHCResponse }

import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, _ }
import scala.language.implicitConversions

class AhcWSRequestSpec extends Specification with Mockito with AfterAll {
  sequential

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val wsClient = StandaloneAhcWSClient()

  override def afterAll: Unit = {
    wsClient.close()
    system.terminate()
  }

  def withClient(block: StandaloneWSClient => Result): Result = {
    block(wsClient)
  }

  "give the full URL with the query string" in {
    implicit val materializer = mock[akka.stream.Materializer]
    val client = mock[StandaloneAhcWSClient]
    val request = StandaloneAhcWSRequest(client, "http://example.com")

    request.uri.toString must equalTo("http://example.com")
    request.withQueryString("bar" -> "baz").uri.toString must equalTo("http://example.com?bar=baz")
    request.withQueryString("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo("http://example.com?bar=bah&bar=baz")

  }

  "correctly URL-encode the query string part" in {
    implicit val materializer = mock[akka.stream.Materializer]
    val client = mock[StandaloneAhcWSClient]
    val request = StandaloneAhcWSRequest(client, "http://example.com")

    request.withQueryString("&" -> "=").uri.toString must equalTo("http://example.com?%26=%3D")

  }

  "AhcWSCookie.underlying" in {
    val mockCookie = mock[AHCCookie]
    val cookie = new AhcWSCookie(mockCookie)
    cookie.underlying[AHCCookie] must beAnInstanceOf[AHCCookie]
  }

  "support several query string values for a parameter" in {
    withClient { client =>
      val req: AHCRequest = client.url("http://playframework.com/")
        .withQueryString("foo" -> "foo1", "foo" -> "foo2").asInstanceOf[StandaloneAhcWSRequest].buildRequest()

      import scala.collection.JavaConverters._
      val paramsList: Seq[Param] = req.getQueryParams.asScala.toSeq
      paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo1")) must beTrue
      paramsList.exists(p => (p.getName == "foo") && (p.getValue == "foo2")) must beTrue
      paramsList.count(p => p.getName == "foo") must beEqualTo(2)
    }

  }

  /*
  "StandaloneAhcWSRequest.setHeaders using a builder with direct map" in new WithApplication {
    val request = new StandaloneAhcWSRequest(mock[AhcWSClient], "GET", None, None, Map.empty, EmptyBody, new RequestBuilder("GET"))
    val headerMap: Map[String, Seq[String]] = Map("key" -> Seq("value"))
    val ahcRequest = request.setHeaders(headerMap).build
    ahcRequest.getHeaders.containsKey("key") must beTrue
  }

  "StandaloneAhcWSRequest.setQueryString" in new WithApplication {
    val request = new StandaloneAhcWSRequest(mock[AhcWSClient], "GET", None, None, Map.empty, EmptyBody, new RequestBuilder("GET"))
    val queryString: Map[String, Seq[String]] = Map("key" -> Seq("value"))
    val ahcRequest = request.setQueryString(queryString).build
    ahcRequest.getQueryParams().containsKey("key") must beTrue
  }

  "support several query string values for a parameter" in new WithApplication {
    val req = WS.url("http://playframework.com/")
      .withQueryString("foo" -> "foo1", "foo" -> "foo2").asInstanceOf[StandaloneAhcWSRequestHolder]
      .prepare().build
    req.getQueryParams.get("foo").contains("foo1") must beTrue
    req.getQueryParams.get("foo").contains("foo2") must beTrue
    req.getQueryParams.get("foo").size must equalTo(2)
  }
  */

  "support http headers" in {
    withClient { client =>
      import scala.collection.JavaConverters._
      val req: AHCRequest = client.url("http://playframework.com/")
        .withHeaders("key" -> "value1", "key" -> "value2").asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      req.getHeaders.getAll("key").asScala must containTheSameElementsAs(Seq("value1", "value2"))
    }
  }

  "not make Content-Type header if there is Content-Type in headers already" in {
    withClient { client =>
      import scala.collection.JavaConverters._
      val req: AHCRequest = client.url("http://playframework.com/")
        .withHeaders("content-type" -> "fake/contenttype; charset=utf-8")
        .withBody(<aaa>value1</aaa>)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      req.getHeaders.getAll("Content-Type").asScala must_== Seq("fake/contenttype; charset=utf-8")
    }
  }

  "Have form params on POST of content type application/x-www-form-urlencoded" in {
    withClient { client =>
      val req: AHCRequest = client.url("http://playframework.com/")
        .withBody(Map("param1" -> Seq("value1")))
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      (new String(req.getByteData, "UTF-8")) must_== ("param1=value1")
    }
  }

  "Have form body on POST of content type text/plain" in {
    withClient { client =>
      val formEncoding = java.net.URLEncoder.encode("param1=value1", "UTF-8")
      val req: AHCRequest = client.url("http://playframework.com/")
        .withHeaders("Content-Type" -> "text/plain")
        .withBody("HELLO WORLD")
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()

      (new String(req.getByteData, "UTF-8")) must be_==("HELLO WORLD")
      val headers = req.getHeaders
      headers.get("Content-Length") must beNull
    }
  }

  "Have form body on POST of content type application/x-www-form-urlencoded explicitly set" in {
    withClient { client =>
      val req: AHCRequest = client.url("http://playframework.com/")
        .withHeaders("Content-Type" -> "application/x-www-form-urlencoded") // set content type by hand
        .withBody("HELLO WORLD") // and body is set to string (see #5221)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      (new String(req.getByteData, "UTF-8")) must be_==("HELLO WORLD") // should result in byte data.
    }
  }

  "support a custom signature calculator" in {
    var called = false
    val calc = new play.shaded.ahc.org.asynchttpclient.SignatureCalculator with WSSignatureCalculator {
      override def calculateAndAddSignature(
        request: play.shaded.ahc.org.asynchttpclient.Request,
        requestBuilder: play.shaded.ahc.org.asynchttpclient.RequestBuilderBase[_]): Unit = {
        called = true
      }
    }
    withClient { client =>
      val req = client.url("http://playframework.com/").sign(calc)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      called must beTrue
    }
  }

  "Have form params on POST of content type application/x-www-form-urlencoded when signed" in {
    withClient { client =>
      import scala.collection.JavaConverters._
      val consumerKey = ConsumerKey("key", "secret")
      val requestToken = RequestToken("token", "secret")
      val calc = OAuthCalculator(consumerKey, requestToken)
      val req: AHCRequest = client.url("http://playframework.com/").withBody(Map("param1" -> Seq("value1")))
        .sign(calc)
        .asInstanceOf[StandaloneAhcWSRequest]
        .buildRequest()
      // Note we use getFormParams instead of getByteData here.
      req.getFormParams.asScala must containTheSameElementsAs(List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")))
      req.getByteData must beNull // should NOT result in byte data.

      val headers = req.getHeaders
      headers.get("Content-Length") must beNull
    }
  }

  "Not remove a user defined content length header" in withClient { client =>
    val consumerKey = ConsumerKey("key", "secret")
    val requestToken = RequestToken("token", "secret")
    val calc = OAuthCalculator(consumerKey, requestToken)
    val req: AHCRequest = client.url("http://playframework.com/").withBody(Map("param1" -> Seq("value1")))
      .withHeaders("Content-Length" -> "9001") // add a meaningless content length here...
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    (new String(req.getByteData, "UTF-8")) must be_==("param1=value1") // should result in byte data.

    val headers = req.getHeaders
    headers.get("Content-Length") must_== ("9001")
  }

  "Remove a user defined content length header if we are parsing body explicitly when signed" in withClient { client =>
    import scala.collection.JavaConverters._
    val consumerKey = ConsumerKey("key", "secret")
    val requestToken = RequestToken("token", "secret")
    val calc = OAuthCalculator(consumerKey, requestToken)
    val req: AHCRequest = client.url("http://playframework.com/").withBody(Map("param1" -> Seq("value1")))
      .withHeaders("Content-Length" -> "9001") // add a meaningless content length here...
      .sign(calc) // this is signed, so content length is no longer valid per #5221
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    val headers = req.getHeaders
    req.getByteData must beNull // should NOT result in byte data.
    req.getFormParams.asScala must containTheSameElementsAs(List(new play.shaded.ahc.org.asynchttpclient.Param("param1", "value1")))
    headers.get("Content-Length") must beNull // no content length!
  }

  "Verify Content-Type header is passed through correctly" in withClient { client =>
    import scala.collection.JavaConverters._
    val req: AHCRequest = client.url("http://playframework.com/")
      .withHeaders("Content-Type" -> "text/plain; charset=US-ASCII")
      .withBody("HELLO WORLD")
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getHeaders.getAll("Content-Type").asScala must_== Seq("text/plain; charset=US-ASCII")
  }

  "POST binary data as is" in withClient { client =>
    val binData = ByteString((0 to 511).map(_.toByte).toArray)
    val req: AHCRequest = client.url("http://playframework.com/").withHeaders("Content-Type" -> "application/x-custom-bin-data").withBody(binData).asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()

    ByteString(req.getByteData) must_== binData
  }

  "support a virtual host" in withClient { client =>
    val req: AHCRequest = client.url("http://playframework.com/")
      .withVirtualHost("192.168.1.1").asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getVirtualHost must be equalTo "192.168.1.1"
  }

  "support follow redirects" in withClient { client =>
    val req: AHCRequest = client.url("http://playframework.com/")
      .withFollowRedirects(follow = true).asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getFollowRedirect must beEqualTo(true)
  }

  "support finite timeout" in withClient { client =>
    val req: AHCRequest = client.url("http://playframework.com/")
      .withRequestTimeout(1000.millis).asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getRequestTimeout must be equalTo 1000
  }

  "support infinite timeout" in withClient { client =>
    val req: AHCRequest = client.url("http://playframework.com/")
      .withRequestTimeout(Duration.Inf).asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getRequestTimeout must be equalTo -1
  }

  "not support negative timeout" in withClient { client =>
    client.url("http://playframework.com/").withRequestTimeout(-1.millis) should throwAn[IllegalArgumentException]
  }

  "not support a timeout greater than Int.MaxValue" in withClient { client =>
    client.url("http://playframework.com/").withRequestTimeout((Int.MaxValue.toLong + 1).millis) should throwAn[IllegalArgumentException]
  }

  "support a proxy server with basic" in withClient { client =>
    val proxy = DefaultWSProxyServer(protocol = Some("https"), host = "localhost", port = 8080, principal = Some("principal"), password = Some("password"))
    val req: AHCRequest = client.url("http://playframework.com/").withProxyServer(proxy).asInstanceOf[StandaloneAhcWSRequest].buildRequest()
    val actual = req.getProxyServer

    actual.getHost must be equalTo "localhost"
    actual.getPort must be equalTo 8080
    actual.getRealm.getPrincipal must be equalTo "principal"
    actual.getRealm.getPassword must be equalTo "password"
    actual.getRealm.getScheme must be equalTo AuthScheme.BASIC
  }

  "support a proxy server with NTLM" in withClient { client =>
    val proxy = DefaultWSProxyServer(protocol = Some("ntlm"), host = "localhost", port = 8080, principal = Some("principal"), password = Some("password"), ntlmDomain = Some("somentlmdomain"))
    val req: AHCRequest = client.url("http://playframework.com/").withProxyServer(proxy).asInstanceOf[StandaloneAhcWSRequest].buildRequest()
    val actual = req.getProxyServer

    actual.getHost must be equalTo "localhost"
    actual.getPort must be equalTo 8080
    actual.getRealm.getPrincipal must be equalTo "principal"
    actual.getRealm.getPassword must be equalTo "password"
    actual.getRealm.getNtlmDomain must be equalTo "somentlmdomain"
    actual.getRealm.getScheme must be equalTo AuthScheme.NTLM
  }

  "Set Realm.UsePreemptiveAuth to false when WSAuthScheme.DIGEST being used" in withClient { client =>
    val req = client.url("http://playframework.com/")
      .withAuth("usr", "pwd", WSAuthScheme.DIGEST)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getRealm.isUsePreemptiveAuth must beFalse
  }

  "Set Realm.UsePreemptiveAuth to true when WSAuthScheme.DIGEST not being used" in withClient { client =>
    val req = client.url("http://playframework.com/")
      .withAuth("usr", "pwd", WSAuthScheme.BASIC)
      .asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
    req.getRealm.isUsePreemptiveAuth must beTrue
  }

  "support a proxy server" in withClient { client =>
    val proxy = DefaultWSProxyServer(host = "localhost", port = 8080)
    val req: AHCRequest = client.url("http://playframework.com/").withProxyServer(proxy).asInstanceOf[StandaloneAhcWSRequest].buildRequest()
    val actual = req.getProxyServer

    actual.getHost must be equalTo "localhost"
    actual.getPort must be equalTo 8080
    actual.getRealm must beNull
  }

}
