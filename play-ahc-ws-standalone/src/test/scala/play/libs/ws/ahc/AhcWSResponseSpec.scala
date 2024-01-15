/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc

import org.mockito.Mockito.when
import org.mockito.Mockito
import org.scalatest.wordspec.AnyWordSpec
import play.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag

class AhcWSResponseSpec extends AnyWordSpec with DefaultBodyReadables with DefaultBodyWritables {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  "getUnderlying" should {

    "return the underlying response" in {
      val srcResponse = mock[Response]
      val response    = new StandaloneAhcWSResponse(srcResponse)
      assert(response.getUnderlying == srcResponse)
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
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)
      val headers  = response.getHeaders
      assert(headers.get("foo").asScala == Seq("a", "b", "b"))
      assert(headers.get("BAR").asScala == Seq("baz"))
    }

    "get a single header" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)

      assert(response.getSingleHeader("Foo").toScala == Some("a"))
      assert(response.getSingleHeader("Bar").toScala == Some("baz"))
    }

    "get an empty optional when header is not present" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)

      assert(response.getSingleHeader("Non").toScala.isEmpty)
    }

    "get all values for a header" in {
      val srcResponse = mock[Response]
      val srcHeaders = new DefaultHttpHeaders()
        .add("Foo", "a")
        .add("foo", "b")
        .add("FOO", "b")
        .add("Bar", "baz")
      when(srcResponse.getHeaders).thenReturn(srcHeaders)
      val response = new StandaloneAhcWSResponse(srcResponse)

      assert(response.getHeaderValues("Foo").asScala.toSet == Set("a", "b", "b"))
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
