/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.shaded.ahc.org.asynchttpclient.util.HttpUtils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * INTERNAL API: Utilities for handling the response for both Java and Scala APIs
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
}
