/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.mockito.Mockito.when
import org.mockito.Mockito
import org.specs2.mutable._
import play.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag

class AhcWSResponseSpec extends Specification with DefaultBodyReadables with DefaultBodyWritables {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  "getUnderlying" should {

    "return the underlying response" in {
      val srcResponse = mock[Response]
      val response    = new StandaloneAhcWSResponse(srcResponse)
      response.getUnderlying must_== srcResponse
    }

  }

  "get headers" should {

    "get headers map which retrieves headers case insensitively" in {
      val srcResponse = mock[Response]
      val srcHeaders  = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)
      val headers  = response.getHeaders
      headers.get("foo").asScala must_== Seq("a", "b", "b")
      headers.get("BAR").asScala must_== Seq("baz")
    }

    "get headers map which retrieves headers case insensitively (for streamed responses)" in {
      val srcHeaders = Map("Foo" -> Seq("a"), "foo" -> Seq("b"), "FOO" -> Seq("b"), "Bar" -> Seq("baz"))
      val response   = new StreamedResponse(null, 200, "", null, srcHeaders, null, true)
      val headers    = response.getHeaders
      headers.get("foo").asScala must_== Seq("a", "b", "b")
      headers.get("BAR").asScala must_== Seq("baz")
    }

    "get a single header" in {
      val srcResponse = mock[Response]
      val srcHeaders  = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)

      response.getSingleHeader("Foo").toScala must beSome("a")
      response.getSingleHeader("Bar").toScala must beSome("baz")
    }

    "get an empty optional when header is not present" in {
      val srcResponse = mock[Response]
      val srcHeaders  = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)

      response.getSingleHeader("Non").toScala must beNone
    }

    "get all values for a header" in {
      val srcResponse = mock[Response]
      val srcHeaders  = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
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
