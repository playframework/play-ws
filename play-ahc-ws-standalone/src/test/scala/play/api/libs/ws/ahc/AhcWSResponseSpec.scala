/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import java.util

import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.ws._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.cookie.{ Cookie => AHCCookie }
import play.shaded.ahc.org.asynchttpclient.{ Response => AHCResponse }

/**
 *
 */
class AhcWSResponseSpec extends Specification with Mockito with DefaultBodyReadables with DefaultBodyWritables {

  "Ahc WS Response" should {
    "get cookies from an AHC response" in {

      val ahcResponse: AHCResponse = mock[AHCResponse]
      val (name, value, wrap, domain, path, maxAge, secure, httpOnly) =
        ("someName", "someValue", true, "example.com", "/", 1000L, false, false)

      val ahcCookie: AHCCookie = new AHCCookie(name, value, wrap, domain, path, maxAge, secure, httpOnly)
      ahcResponse.getCookies returns util.Arrays.asList(ahcCookie)

      val response = StandaloneAhcWSResponse(ahcResponse)

      val cookies: Seq[WSCookie] = response.cookies
      val cookie = cookies.head

      cookie.name must ===(name)
      cookie.value must ===(value)
      cookie.path must beSome(path)
      cookie.domain must beSome(domain)
      cookie.maxAge must beSome(maxAge)
      cookie.secure must beFalse
      cookie.httpOnly must beFalse
    }

    "get a single cookie from an AHC response" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val (name, value, wrap, domain, path, maxAge, secure, httpOnly) =
        ("someName", "someValue", true, "example.com", "/", 1000L, false, false)

      val ahcCookie: AHCCookie = new AHCCookie(name, value, wrap, domain, path, maxAge, secure, httpOnly)
      ahcResponse.getCookies returns util.Arrays.asList(ahcCookie)

      val response = StandaloneAhcWSResponse(ahcResponse)

      val optionCookie = response.cookie("someName")
      optionCookie must beSome[WSCookie].which {
        cookie =>
          cookie.name must ===(name)
          cookie.value must ===(value)
          cookie.path must beSome(path)
          cookie.domain must beSome(domain)
          cookie.maxAge must beSome(maxAge)
          cookie.secure must beFalse
          cookie.httpOnly must beFalse
      }
    }

    "return -1 values of expires and maxAge as None" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]

      val ahcCookie: AHCCookie = new AHCCookie("someName", "value", true, "domain", "path", -1L, false, false)
      ahcResponse.getCookies returns util.Arrays.asList(ahcCookie)

      val response = StandaloneAhcWSResponse(ahcResponse)

      val optionCookie = response.cookie("someName")
      optionCookie must beSome[WSCookie].which { cookie =>
        cookie.maxAge must beNone
      }
    }

    "get the body as bytes from the AHC response" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val bytes = ByteString(-87, -72, 96, -63, -32, 46, -117, -40, -128, -7, 61, 109, 80, 45, 44, 30)
      ahcResponse.getResponseBodyAsBytes returns bytes.toArray
      val response = StandaloneAhcWSResponse(ahcResponse)
      response.body[ByteString] must_== bytes
    }

    "get headers from an AHC response in a case insensitive map" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val ahcHeaders = new DefaultHttpHeaders(true)
      ahcHeaders.add("Foo", "bar")
      ahcHeaders.add("Foo", "baz")
      ahcHeaders.add("Bar", "baz")
      ahcResponse.getHeaders returns ahcHeaders
      val response = StandaloneAhcWSResponse(ahcResponse)
      val headers = response.headers
      headers must beEqualTo(Map("Foo" -> Seq("bar", "baz"), "Bar" -> Seq("baz")))
      headers.contains("foo") must beTrue
      headers.contains("Foo") must beTrue
      headers.contains("BAR") must beTrue
      headers.contains("Bar") must beTrue
    }

    "get a single header" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val ahcHeaders = new DefaultHttpHeaders(true)
      ahcHeaders.add("Foo", "bar")
      ahcHeaders.add("Foo", "baz")
      ahcHeaders.add("Bar", "baz")
      ahcResponse.getHeaders returns ahcHeaders
      val response = StandaloneAhcWSResponse(ahcResponse)

      response.header("Foo") must beSome("bar")
      response.header("Bar") must beSome("baz")
    }

    "get none when header does not exists" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val ahcHeaders = new DefaultHttpHeaders(true)
      ahcHeaders.add("Foo", "bar")
      ahcHeaders.add("Foo", "baz")
      ahcHeaders.add("Bar", "baz")
      ahcResponse.getHeaders returns ahcHeaders
      val response = StandaloneAhcWSResponse(ahcResponse)

      response.header("Non") must beNone
    }

    "get all values for a header" in {
      val ahcResponse: AHCResponse = mock[AHCResponse]
      val ahcHeaders = new DefaultHttpHeaders(true)
      ahcHeaders.add("Foo", "bar")
      ahcHeaders.add("Foo", "baz")
      ahcHeaders.add("Bar", "baz")
      ahcResponse.getHeaders returns ahcHeaders
      val response = StandaloneAhcWSResponse(ahcResponse)

      response.headerValues("Foo") must beEqualTo(Seq("bar", "baz"))
    }
  }

}
