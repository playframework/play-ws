/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.nio.charset.StandardCharsets

import org.slf4j.LoggerFactory
import play.api.libs.ws._
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils
import play.api.libs.ws.EmptyBody

/**
 * Logs StandaloneWSRequest and pulls information into Curl format to an SLF4J logger.
 *
 * Warning: the command is logged at INFO, without redaction. It can contain passwords, `Authorization` and
 * `Proxy-Authorization` headers, session cookies, credentials and other secrets in the URL, and in-memory request
 * bodies. Use it only for controlled debugging, and do not enable it for traffic that carries secrets.
 *
 * @param logger an SLF4J logger
 *
 * @see <a href="https://curl.haxx.se/">https://curl.haxx.se/</a>
 */
class AhcCurlRequestLogger(logger: org.slf4j.Logger) extends WSRequestFilter with CurlFormat {
  def apply(executor: WSRequestExecutor): WSRequestExecutor = {
    WSRequestExecutor { request =>
      logger.info(toCurl(request.asInstanceOf[StandaloneAhcWSRequest]))
      executor(request)
    }
  }
}

object AhcCurlRequestLogger {

  private val logger = LoggerFactory.getLogger("play.api.libs.ws.ahc.AhcCurlRequestLogger")

  private val instance = new AhcCurlRequestLogger(logger)

  def apply() = instance

  def apply(logger: org.slf4j.Logger): AhcCurlRequestLogger = {
    new AhcCurlRequestLogger(logger)
  }
}

trait CurlFormat {
  def toCurl(request: StandaloneAhcWSRequest): String = {
    // pull out some underlying values from the request. This creates a new Request, without a streamed body, so
    // that the stream is left to the request itself.
    val streamed         = request.body.isInstanceOf[SourceBody]
    val asyncHttpRequest = if (streamed) request.buildRequestWithoutBody() else request.buildRequest()

    val b = new StringBuilder("curl \\\n")

    // verbose, since it's a fair bet this is for debugging
    b.append("  --verbose")
    b.append(" \\\n")

    // method
    b.append(s"  --request ${request.method}")
    b.append(" \\\n")

    // authentication: the header AHC sends for Basic. Other schemes don't send the password as it is.
    Option(asyncHttpRequest.getRealm).filter(_.getScheme == AuthScheme.BASIC).foreach { realm =>
      b.append(s"""  --header 'Authorization: ${quote(realm.getBasicAuthHeader)}'""")
      b.append(" \\\n")
    }

    // headers, without the length of a body that the command leaves out, so that the server doesn't wait for it
    request.headers.foreach { case (k, values) =>
      val bodyLength = k.equalsIgnoreCase("Content-Length") || k.equalsIgnoreCase("Transfer-Encoding")
      if (!streamed || !bodyLength) values.foreach { v =>
        b.append(s"  --header '${quote(k)}: ${quote(v)}'")
        b.append(" \\\n")
      }
    }

    // cookies
    request.cookies.foreach { cookie =>
      b.append(s"""  --cookie '${quote(cookie.name)}=${quote(cookie.value)}'""")
      b.append(" \\\n")
    }

    // body (note that this has only been checked for text, not binary)
    request.body match {
      case InMemoryBody(byteString) =>
        val charset    = findCharset(request)
        val bodyString = byteString.decodeString(charset)
        // XXX Need to escape any quotes within the body of the string.
        b.append(s"  --data '${quote(bodyString)}'")
        b.append(" \\\n")
      case EmptyBody     => // Do nothing.
      case SourceBody(_) => // A stream can be read only once, so the request reads it, not this.
    }

    val proxyServer = asyncHttpRequest.getProxyServer
    if (proxyServer != null) {
      b.append(s"  --proxy ${proxyServer.getHost}:${proxyServer.getPort}")
      b.append(" \\\n")
    }

    // url
    b.append(s"  '${quote(asyncHttpRequest.getUrl)}'")

    if (streamed) {
      b.append("\n# The streamed request body is not shown.")
    }

    val curlOptions = b.toString()
    curlOptions
  }

  protected def findCharset(request: StandaloneAhcWSRequest): String = {
    request.contentType
      .map { ct =>
        Option(HttpUtils.extractContentTypeCharsetAttribute(ct))
          .getOrElse {
            StandardCharsets.UTF_8
          }
          .name()
      }
      .getOrElse(HttpUtils.extractContentTypeCharsetAttribute("UTF-8").name())
  }

  def quote(unsafe: String): String = unsafe.replace("'", "'\\''")
}
