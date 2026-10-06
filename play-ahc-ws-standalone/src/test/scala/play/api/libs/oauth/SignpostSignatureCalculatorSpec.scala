/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.oauth

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.asynchttpclient.oauth.OAuthSignatureCalculatorTestSupport
import org.asynchttpclient.{ RequestBuilder => Ahc2RequestBuilder }
import org.specs2.mutable.Specification
import play.shaded.ahc.org.asynchttpclient.{ RequestBuilder => Ahc3RequestBuilder }

class SignpostSignatureCalculatorSpec extends Specification {

  "SignpostSignatureCalculator" should {
    "produce the RFC 5849 signature with AHC's version parameter" in {
      val parameters = Seq(
        "b5"                     -> "=%3D",
        "a3"                     -> "a",
        "c@"                     -> "",
        "a2"                     -> "r b",
        "oauth_consumer_key"     -> "9djdj82h48djs9d2",
        "oauth_token"            -> "kkk9d7dh3k39sjv7",
        "oauth_signature_method" -> "HMAC-SHA1",
        "oauth_timestamp"        -> "137131201",
        "oauth_nonce"            -> "7d8f3e4a",
        "c2"                     -> "",
        "a3"                     -> "2 q"
      )
      val rfcSignature = hmacSha1Signature(
        "POST",
        "http://example.com/request",
        parameters,
        "j49sk3j29djd",
        "dh893hdasih9"
      )

      // RFC 5849 omits oauth_version in this example. AHC 2 always included
      // and signed it, so Play WS retains that behavior in the actual request.
      // RFC 5849 erratum 2550 corrects the signature printed in the RFC.
      rfcSignature must beEqualTo("r6/TJjbCOr97/+UU0NsvSne7s5g=")

      val builder = new Ahc3RequestBuilder("POST", true)
        .setUrl("http://example.com/request?b5=%3D%253D&a3=a&c%40=&a2=r%20b")
        .setHeader("Content-Type", "application/x-www-form-urlencoded")
        .addFormParam("c2", "")
        .addFormParam("a3", "2 q")

      val header = signWithAhc3(
        builder,
        consumerKey = "9djdj82h48djs9d2",
        consumerSecret = "j49sk3j29djd",
        token = "kkk9d7dh3k39sjv7",
        tokenSecret = "dh893hdasih9",
        timestamp = 137131201L,
        nonce = "7d8f3e4a"
      )

      val compatibleSignature = hmacSha1Signature(
        "POST",
        "http://example.com/request",
        parameters :+ ("oauth_version" -> "1.0"),
        "j49sk3j29djd",
        "dh893hdasih9"
      )
      compatibleSignature must beEqualTo("OB33pYjWAnf+xtOHN4Gmbdil168=")
      oauthParameters(header).get("oauth_signature") must beSome(compatibleSignature)
    }

    "match AHC 2.16.1 for encoded requests" in {
      val cases = Seq(
        SigningCase(
          "query parameters",
          "GET",
          "https://example.com/resource",
          queryParams = Seq(
            "space"        -> "a b",
            "plus"         -> "a+b",
            "encoded-plus" -> "%2B",
            "encoded"      -> "%7Evalue%2Fpart",
            "repeat"       -> "first",
            "repeat"       -> "second",
            "reserved"     -> ":/?#[]@!$&'()*+,;="
          )
        ),
        SigningCase(
          "form parameters",
          "POST",
          "https://example.com/form",
          formParams = Seq(
            "space"    -> "a b",
            "plus"     -> "a+b",
            "repeat"   -> "first",
            "repeat"   -> "second",
            "reserved" -> ":/?#[]@!$&'()*+,;="
          )
        ),
        SigningCase(
          "query and form parameters together",
          "POST",
          "https://example.com/combined?embedded=from%20url&shared=embedded",
          queryParams = Seq("query" -> "builder value", "shared" -> "query"),
          formParams = Seq("form" -> "body value", "shared" -> "form")
        ),
        SigningCase(
          "UTF-8 query and form parameters",
          "POST",
          "https://example.com/unicode?embedded=caf%C3%A9",
          queryParams = Seq("query-\u00e4" -> "Gr\u00fc\u00dfe \u6771\u4eac"),
          formParams = Seq("form-\u00f8" -> "cr\u00e8me br\u00fbl\u00e9e")
        ),
        SigningCase("default HTTP port and encoded path", "GET", "http://example.com:80/r%20v/X?id=123"),
        SigningCase("default HTTPS port", "GET", "https://example.com:443/resource?q=1"),
        SigningCase("non-default port", "GET", "https://example.com:8443/resource?q=1"),
        SigningCase("encoded path", "GET", "https://example.com/r%2Fv/%7Euser?q=value"),
        SigningCase(
          "URL-embedded raw plus and repeated query",
          "GET",
          "http://example.com/resource?raw=a+b&encoded=a%2Bb&space=a%20b&repeat=1&repeat=2"
        )
      )

      cases.foreach { signingCase =>
        val ahc3 = signWithAhc3(buildAhc3Request(signingCase))
        val ahc2 = signWithAhc2(buildAhc2Request(signingCase))

        oauthParameters(ahc3) must beEqualTo(oauthParameters(ahc2)).updateMessage(message =>
          s"${signingCase.name}: $message"
        )
      }
      success
    }

    "treat a bare query parameter as an empty value" in {
      val bare = signWithAhc3(
        new Ahc3RequestBuilder("GET").setUrl("https://example.com/resource?flag&empty=")
      )
      val explicit = signWithAhc3(
        new Ahc3RequestBuilder("GET").setUrl("https://example.com/resource?flag=&empty=")
      )

      oauthParameters(bare) must beEqualTo(oauthParameters(explicit))
    }
  }

  private val consumerKey    = "consumer key"
  private val consumerSecret = "consumer&secret"
  private val token          = "token value"
  private val tokenSecret    = "token/secret"
  private val timestamp      = 1700000000L
  private val nonce          = "fixed-nonce-value"

  private def buildAhc3Request(signingCase: SigningCase): Ahc3RequestBuilder = {
    val builder = new Ahc3RequestBuilder(signingCase.method).setUrl(signingCase.url)
    signingCase.queryParams.foreach { case (name, value) => builder.addQueryParam(name, value) }
    signingCase.formParams.foreach { case (name, value) => builder.addFormParam(name, value) }
    if (signingCase.formParams.nonEmpty) {
      builder.setHeader("Content-Type", "application/x-www-form-urlencoded")
    }
    builder
  }

  private def buildAhc2Request(signingCase: SigningCase): Ahc2RequestBuilder = {
    val builder = new Ahc2RequestBuilder(signingCase.method).setUrl(signingCase.url)
    signingCase.queryParams.foreach { case (name, value) => builder.addQueryParam(name, value) }
    signingCase.formParams.foreach { case (name, value) => builder.addFormParam(name, value) }
    if (signingCase.formParams.nonEmpty) {
      builder.setHeader("Content-Type", "application/x-www-form-urlencoded")
    }
    builder
  }

  private def signWithAhc3(
      builder: Ahc3RequestBuilder,
      consumerKey: String = consumerKey,
      consumerSecret: String = consumerSecret,
      token: String = token,
      tokenSecret: String = tokenSecret,
      timestamp: Long = timestamp,
      nonce: String = nonce
  ): String = {
    builder.setHeader("Authorization", s"OAuth oauth_timestamp=\"$timestamp\", oauth_nonce=\"$nonce\"")
    val request = builder.build()
    new SignpostSignatureCalculator(consumerKey, consumerSecret, token, tokenSecret)
      .calculateAndAddSignature(request, builder)
    builder.build().getHeaders.get("Authorization")
  }

  private def signWithAhc2(builder: Ahc2RequestBuilder): String = {
    val request = builder.build()
    OAuthSignatureCalculatorTestSupport.authorizationHeader(
      request,
      consumerKey,
      consumerSecret,
      token,
      tokenSecret,
      timestamp,
      nonce
    )
  }

  private def oauthParameters(header: String): Map[String, String] = {
    header
      .stripPrefix("OAuth ")
      .split(',')
      .iterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { parameter =>
        val separator = parameter.indexOf('=')
        val name      = parameter.substring(0, separator)
        val value     = parameter.substring(separator + 1).stripPrefix("\"").stripSuffix("\"")
        name -> URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
      }
      .toMap
  }

  private def hmacSha1Signature(
      method: String,
      baseUrl: String,
      parameters: Seq[(String, String)],
      consumerSecret: String,
      tokenSecret: String
  ): String = {
    val normalizedParameters = parameters
      .map { case (name, value) => percentEncode(name) -> percentEncode(value) }
      .sortBy(identity)
      .map { case (name, value) => s"$name=$value" }
      .mkString("&")
    val baseString = Seq(method, baseUrl, normalizedParameters).map(percentEncode).mkString("&")
    val signingKey = s"${percentEncode(consumerSecret)}&${percentEncode(tokenSecret)}"
    val mac        = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"))
    Base64.getEncoder.encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)))
  }

  private def percentEncode(value: String): String = {
    URLEncoder
      .encode(value, StandardCharsets.UTF_8)
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~")
  }
}

private final case class SigningCase(
    name: String,
    method: String,
    url: String,
    queryParams: Seq[(String, String)] = Seq.empty,
    formParams: Seq[(String, String)] = Seq.empty
)
