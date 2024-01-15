/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets
import java.util

import org.apache.pekko.util.ByteString
import org.mockito.Mockito.when
import org.mockito.Mockito
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.io.netty.handler.codec.http.cookie.DefaultCookie
import play.shaded.ahc.io.netty.handler.codec.http.cookie.{ Cookie => AHCCookie }
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }

import scala.reflect.ClassTag

class AhcWSResponseSpec extends AnyWordSpec with DefaultBodyReadables with DefaultBodyWritables with OptionValues {

  private def mock[A](implicit a: ClassTag[A]): A =
    Mockito.mock(a.runtimeClass).asInstanceOf[A]

  "Ahc WS Response" should {
    "get cookies from an AHC response" in {

      val ahcResponse: AHCResponse = mock[AHCResponse]
      val (name, value, wrap, domain, path, maxAge, secure, httpOnly) =
        ("someName", "someValue", true, "example.com", "/", 1000L, false, false)

      val ahcCookie: AHCCookie = asCookie(name, value, wrap, domain, path, maxAge, secure, httpOnly)
      when(ahcResponse.getCookies).thenReturn(util.Arrays.asList(ahcCookie))

      val response = StandaloneAhcWSResponse(ahcResponse)

      val cookies: Seq[WSCookie] = response.cookies
      val cookie                 = cookies.head

      assert(cookie.name == name)
      assert(cookie.value == value)
      assert(cookie.path == Some(path))
      assert(cookie.domain == Some(domain))
      assert(cookie.maxAge == Some(maxAge))
      assert(cookie.secure == false)
      assert(cookie.httpOnly == false)
    }

    "get a single cookie from an AHC response" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val (name, value, wrap, domain, path, maxAge, secure, httpOnly) =
        ("someName", "someValue", true, "example.com", "/", 1000L, false, false)

      val ahcCookie: AHCCookie = asCookie(name, value, wrap, domain, path, maxAge, secure, httpOnly)
      when(ahcResponse.getCookies).thenReturn(util.Arrays.asList(ahcCookie))

      val response = StandaloneAhcWSResponse(ahcResponse)

      val cookie = response.cookie("someName").value
      assert(cookie.name == name)
      assert(cookie.value == value)
      assert(cookie.path == Some(path))
      assert(cookie.domain == Some(domain))
      assert(cookie.maxAge == Some(maxAge))
      assert(cookie.secure == false)
      assert(cookie.httpOnly == false)
    }

    "return -1 values of expires and maxAge as None" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]

      val ahcCookie: AHCCookie = asCookie("someName", "value", true, "domain", "path", -1L, false, false)
      when(ahcResponse.getCookies).thenReturn(util.Arrays.asList(ahcCookie))

      val response = StandaloneAhcWSResponse(ahcResponse)

      val cookie = response.cookie("someName").value
      assert(cookie.maxAge.isEmpty)
    }

    "get the body as bytes from the AHC response" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val bytes = ByteString(-87, -72, 96, -63, -32, 46, -117, -40, -128, -7, 61, 109, 80, 45, 44, 30)
      when(ahcResponse.getResponseBodyAsBytes).thenReturn(bytes.toArray)
      val response = StandaloneAhcWSResponse(ahcResponse)
      assert(response.body[ByteString] == bytes)
    }

    "get JSON body as a string from the AHC response" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val json                     = """{ "foo": "☺" }"""
      val ahcHeaders               = new DefaultHttpHeaders(true)
      when(ahcResponse.getContentType).thenReturn("application/json")
      when(ahcResponse.getHeaders).thenReturn(ahcHeaders)
      when(ahcResponse.getResponseBody(StandardCharsets.UTF_8)).thenReturn(json)
      val response = StandaloneAhcWSResponse(ahcResponse)
      assert(response.body[String] == json)
    }
  }

  "get text body as a string from the AHC response" in {
    val ahcResponse: AHCResponse = mock[AHCResponse]
    val text                     = "Hello ☺"
    when(ahcResponse.getContentType).thenReturn("text/plain")
    when(ahcResponse.getResponseBody(StandardCharsets.ISO_8859_1)).thenReturn(text)
    val response = StandaloneAhcWSResponse(ahcResponse)
    assert(response.body[String] == text)
  }

  "get headers from an AHC response in a case insensitive map" in {
    val ahcResponse: AHCResponse = mock[AHCResponse]
    val ahcHeaders               = new DefaultHttpHeaders(true)
    ahcHeaders.add("Foo", "bar")
    ahcHeaders.add("Foo", "baz")
    ahcHeaders.add("Bar", "baz")
    when(ahcResponse.getHeaders).thenReturn(ahcHeaders)
    val response = StandaloneAhcWSResponse(ahcResponse)
    val headers  = response.headers
    assert(headers == Map("Foo" -> Seq("bar", "baz"), "Bar" -> Seq("baz")))
    assert(headers.contains("foo"))
    assert(headers.contains("Foo"))
    assert(headers.contains("BAR"))
    assert(headers.contains("Bar"))
  }

  "get a single header" in {
    val ahcResponse: AHCResponse = mock[AHCResponse]
    val ahcHeaders               = new DefaultHttpHeaders(true)
    ahcHeaders.add("Foo", "bar")
    ahcHeaders.add("Foo", "baz")
    ahcHeaders.add("Bar", "baz")
    when(ahcResponse.getHeaders).thenReturn(ahcHeaders)
    val response = StandaloneAhcWSResponse(ahcResponse)

    assert(response.header("Foo") == Some("bar"))
    assert(response.header("Bar") == Some("baz"))
  }

  "get none when header does not exists" in {
    val ahcResponse: AHCResponse = mock[AHCResponse]
    val ahcHeaders               = new DefaultHttpHeaders(true)
    ahcHeaders.add("Foo", "bar")
    ahcHeaders.add("Foo", "baz")
    ahcHeaders.add("Bar", "baz")
    when(ahcResponse.getHeaders).thenReturn(ahcHeaders)
    val response = StandaloneAhcWSResponse(ahcResponse)

    assert(response.header("Non").isEmpty)
  }

  "get all values for a header" in {
    val ahcResponse: AHCResponse = mock[AHCResponse]
    val ahcHeaders               = new DefaultHttpHeaders(true)
    ahcHeaders.add("Foo", "bar")
    ahcHeaders.add("Foo", "baz")
    ahcHeaders.add("Bar", "baz")
    when(ahcResponse.getHeaders).thenReturn(ahcHeaders)
    val response = StandaloneAhcWSResponse(ahcResponse)

    assert(response.headerValues("Foo") == Seq("bar", "baz"))
  }

  def asCookie(
      name: String,
      value: String,
      wrap: Boolean,
      domain: String,
      path: String,
      maxAge: Long,
      secure: Boolean,
      httpOnly: Boolean
  ): AHCCookie = {
    val c = new DefaultCookie(name, value)
    c.setWrap(wrap)
    c.setDomain(domain)
    c.setPath(path)
    c.setMaxAge(maxAge)
    c.setSecure(secure)
    c.setHttpOnly(httpOnly)
    c
  }

}
