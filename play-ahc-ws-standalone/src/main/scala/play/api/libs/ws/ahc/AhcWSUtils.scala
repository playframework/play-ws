/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.shaded.ahc.org.asynchttpclient.util.HttpUtils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

/**
 * INTERNAL API: Utilities for handling requests and responses for both Java and Scala APIs
 */
private[ws] object AhcWSUtils {
  def getResponseBody(ahcResponse: play.shaded.ahc.org.asynchttpclient.Response): String = {
    val contentType = Option(ahcResponse.getContentType).getOrElse("application/octet-stream")
    val charset     = getCharset(contentType)
    ahcResponse.getResponseBody(charset)
  }

  def getCharset(contentType: String): Charset = {
    Option(HttpUtils.extractContentTypeCharsetAttribute(contentType)).getOrElse {
      if (contentType.startsWith("text/"))
        StandardCharsets.ISO_8859_1
      else
        StandardCharsets.UTF_8
    }
  }

  def normalizeRequestContentType(headers: HttpHeaders): Unit = {
    val contentType = headers.get(HttpHeaderNames.CONTENT_TYPE)
    if (
      contentType != null &&
      contentType.regionMatches(true, 0, "text/", 0, 5) &&
      HttpUtils.extractContentTypeCharsetAttribute(contentType) == null
    ) {
      headers.set(HttpHeaderNames.CONTENT_TYPE, s"$contentType; charset=${StandardCharsets.UTF_8.name()}")
    }
  }
}
