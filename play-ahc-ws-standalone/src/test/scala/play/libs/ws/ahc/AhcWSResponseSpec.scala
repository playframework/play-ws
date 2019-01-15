/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import java.nio.charset.StandardCharsets

import org.specs2.mock.Mockito
import org.specs2.mutable._
import play.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

class AhcWSResponseSpec extends Specification with Mockito with DefaultBodyReadables with DefaultBodyWritables {

  private val emptyMap = new java.util.HashMap[String, java.util.Collection[String]]

  "getUnderlying" should {

    "return the underlying response" in {
      val srcResponse = mock[Response]
      val response = new StandaloneAhcWSResponse(srcResponse)
      response.getUnderlying must_== srcResponse
    }

  }

  "get headers" should {

    "get headers map which retrieves headers case insensitively" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      srcResponse.getHeaders returns srcHeaders
      val response = new StandaloneAhcWSResponse(srcResponse)
      val headers = response.getHeaders
      headers.get("foo").asScala must_== Seq("a", "b", "b")
      headers.get("BAR").asScala must_== Seq("baz")
    }

    "get a single header" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      srcResponse.getHeaders returns srcHeaders
      val response = new StandaloneAhcWSResponse(srcResponse)

      response.getSingleHeader("Foo").asScala must beSome("a")
      response.getSingleHeader("Bar").asScala must beSome("baz")
    }

    "get an empty optional when header is not present" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      srcResponse.getHeaders returns srcHeaders
      val response = new StandaloneAhcWSResponse(srcResponse)

      response.getSingleHeader("Non").asScala must beNone
    }

    "get all values for a header" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      srcResponse.getHeaders returns srcHeaders
      val response = new StandaloneAhcWSResponse(srcResponse)

      response.getHeaderValues("Foo").asScala must containTheSameElementsAs(Seq("a", "b", "b"))
    }
  }

  /*
    getStatus
    getStatusText
    getHeader
    getCookies
    getCookie
    getBody
    asXml
    asJson
    getBodyAsStream
    asByteArray
    getUriOption
    */

}
