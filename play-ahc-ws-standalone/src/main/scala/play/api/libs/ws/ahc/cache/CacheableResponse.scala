/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws.ahc.cache

import java.io.{ ByteArrayInputStream, IOException, InputStream }
import java.net.{ MalformedURLException, SocketAddress }
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util

import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names._
import play.shaded.ahc.io.netty.handler.codec.http.{ DefaultHttpHeaders, HttpHeaders }
import play.shaded.ahc.org.asynchttpclient._
import play.shaded.ahc.org.asynchttpclient.cookie.Cookie
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils._

class CacheableResponseBuilder {

  private var bodyParts: List[CacheableHttpResponseBodyPart] = Nil
  private var status: Option[CacheableHttpResponseStatus] = None
  private var headers: Option[CacheableHttpResponseHeaders] = None

  def accumulate(responseStatus: HttpResponseStatus): CacheableResponseBuilder = {
    // https://github.com/AsyncHttpClient/async-http-client/blob/2.0/client/src/main/java/org/asynchttpclient/HttpResponseStatus.java
    val uri = responseStatus.getUri
    val statusCode = responseStatus.getStatusCode
    val statusText = responseStatus.getStatusText
    val protocolText = responseStatus.getProtocolText
    val cacheableStatus = new CacheableHttpResponseStatus(uri, statusCode, statusText, protocolText)

    status = Some(cacheableStatus)
    this
  }

  def accumulate(responseHeaders: HttpResponseHeaders): CacheableResponseBuilder = {
    val cacheableHeaders = CacheableHttpResponseHeaders(responseHeaders.isTrailling, responseHeaders.getHeaders)
    headers = Some(cacheableHeaders)
    this
  }

  def accumulate(bodyPart: HttpResponseBodyPart): CacheableResponseBuilder = {
    val cacheableBodypart = new CacheableHttpResponseBodyPart(bodyPart.getBodyPartBytes, bodyPart.isLast)
    bodyParts = bodyParts :+ cacheableBodypart
    this
  }

  def reset() = {
    headers = None
    status = None
    bodyParts = Nil
  }

  def build: CacheableResponse = {
    import scala.collection.JavaConverters._
    new CacheableResponse(status.get, headers.get, bodyParts.asJava)
  }
}

// https://github.com/AsyncHttpClient/async-http-client/blob/2.0/client/src/main/java/org/asynchttpclient/netty/NettyResponse.java
case class CacheableResponse(
    status: CacheableHttpResponseStatus,
    headers: CacheableHttpResponseHeaders,
    bodyParts: util.List[CacheableHttpResponseBodyPart]) extends Response {

  private var cookies: util.List[Cookie] = _

  import CacheableResponse._

  private val uri: Uri = status.getUri

  def ahcStatus: HttpResponseStatus = status.asInstanceOf[HttpResponseStatus]

  def ahcHeaders: HttpResponseHeaders = headers.asInstanceOf[HttpResponseHeaders]

  def ahcbodyParts: util.List[HttpResponseBodyPart] = bodyParts.asInstanceOf[util.List[HttpResponseBodyPart]]

  def withHeaders(tuple: (String, String)*): CacheableResponse = {
    val headerMap = new DefaultHttpHeaders().add(this.headers.headers)
    tuple.foreach {
      case (k, v) =>
        headerMap.add(k, v)
    }
    val newHeaders = CacheableHttpResponseHeaders(this.headers.trailingHeaders, headerMap)
    this.copy(headers = newHeaders)
  }

  override def getStatusCode: Int = {
    status.getStatusCode
  }

  override def getStatusText: String = {
    status.getStatusText
  }

  @throws(classOf[IOException])
  override def getResponseBodyAsBytes: Array[Byte] = {
    getResponseBodyAsByteBuffer.array()
  }

  @throws(classOf[IOException])
  override def getResponseBodyAsByteBuffer: ByteBuffer = {
    import scala.collection.JavaConverters._
    val length = bodyParts.asScala.map(_.length()).sum
    val target = ByteBuffer.wrap(new Array[Byte](length))
    bodyParts.asScala.foreach(part => target.put(part.getBodyPartBytes))
    target.flip()
    target
  }

  private def computeCharset(charset: Charset): Charset = {
    Option(charset).orElse(Option(getContentType).map(parseCharset)).getOrElse(DEFAULT_CHARSET)
  }

  @throws(classOf[IOException])
  override def getResponseBody: String = {
    if (logger.isTraceEnabled) {
      logger.trace("getResponseBody: ")
    }
    getResponseBody(null)
  }

  @throws(classOf[IOException])
  override def getResponseBody(charset: Charset): String = {
    if (logger.isTraceEnabled) {
      logger.trace("getResponseBody: ")
    }
    new String(getResponseBodyAsBytes, computeCharset(charset))
  }

  @throws(classOf[IOException])
  override def getResponseBodyAsStream: InputStream = {
    if (logger.isTraceEnabled) {
      logger.trace("getResponseBodyAsStream: ")
    }
    new ByteArrayInputStream(getResponseBodyAsBytes)
  }

  @throws(classOf[MalformedURLException])
  override def getUri: Uri = {
    uri
  }

  override def getContentType: String = {
    getHeader(CONTENT_TYPE)
  }

  override def getHeader(name: String): String = {
    headers.getHeaders.get(name)
  }

  override def getHeaders(name: String): util.List[String] = {
    headers.getHeaders.getAll(name)
  }

  override def getHeaders: HttpHeaders = {
    headers.getHeaders
  }

  override def isRedirected: Boolean = {
    status.getStatusCode match {
      case 301 | 302 | 303 | 307 | 308 =>
        true
      case _ =>
        false
    }
  }

  override def getCookies: util.List[Cookie] = {
    import java.util.Collections

    if (headers == null) {
      return Collections.emptyList[Cookie]
    }

    if (cookies == null) {
      cookies = buildCookies
    }
    cookies
  }

  override def hasResponseStatus: Boolean = {
    status != null
  }

  override def hasResponseHeaders: Boolean = {
    headers != null
  }

  override def hasResponseBody: Boolean = {
    !bodyParts.isEmpty
  }

  private def buildCookies: util.List[Cookie] = {
    import play.shaded.ahc.org.asynchttpclient.util.MiscUtils.isNonEmpty
    import play.shaded.ahc.org.asynchttpclient.cookie.CookieDecoder
    import java.util.Collections

    var setCookieHeaders = headers.getHeaders.getAll(SET_COOKIE2)
    if (!isNonEmpty(setCookieHeaders)) setCookieHeaders = headers.getHeaders.getAll(SET_COOKIE)
    if (isNonEmpty(setCookieHeaders)) {
      val cookies = new util.ArrayList[Cookie](1)
      import scala.collection.JavaConversions._
      for (value <- setCookieHeaders) {
        val c = CookieDecoder.decode(value)
        if (c != null) cookies.add(c)
      }
      return Collections.unmodifiableList(cookies)
    }
    Collections.emptyList[Cookie]
  }

  override def toString: String = {
    s"CacheableResponse(status = $status, headers = $headers, bodyParts size = ${bodyParts.size()})"
  }

  override def getLocalAddress: SocketAddress = status.getLocalAddress

  override def getRemoteAddress: SocketAddress = status.getRemoteAddress
}

case class CacheableHttpResponseHeaders(trailingHeaders: Boolean, headers: HttpHeaders)
    extends HttpResponseHeaders(headers, trailingHeaders) {

  override def toString: String = {
    s"CacheableHttpResponseHeaders(trailingHeaders = $trailingHeaders, headers = $headers)"
  }
}

object CacheableResponse {
  private val logger = LoggerFactory.getLogger("play.api.libs.ws.ahc.cache.CacheableResponse")

  def apply(code: Int, urlString: String): CacheableResponse = {
    val uri: Uri = Uri.create(urlString)
    val status = new CacheableHttpResponseStatus(uri, code, "", "")
    val headers = new DefaultHttpHeaders()
    val responseHeaders = CacheableHttpResponseHeaders(trailingHeaders = false, headers = headers)
    val bodyParts = util.Collections.emptyList[CacheableHttpResponseBodyPart]

    CacheableResponse(status = status, headers = responseHeaders, bodyParts = bodyParts)
  }

  def apply(code: Int, urlString: String, body: String): CacheableResponse = {
    val uri: Uri = Uri.create(urlString)
    val status = new CacheableHttpResponseStatus(uri, code, "", "")
    val headers = new DefaultHttpHeaders()
    val responseHeaders = CacheableHttpResponseHeaders(trailingHeaders = false, headers = headers)
    val bodyParts = util.Collections.singletonList(new CacheableHttpResponseBodyPart(body.getBytes, true))

    CacheableResponse(status = status, headers = responseHeaders, bodyParts = bodyParts)
  }
}

// https://github.com/AsyncHttpClient/async-http-client/blob/2.0/client/src/main/java/org/asynchttpclient/netty/NettyResponseStatus.java
class CacheableHttpResponseStatus(
  uri: Uri,
  statusCode: Int,
  statusText: String,
  protocolText: String)
    extends HttpResponseStatus(uri, null) {
  override def getStatusCode: Int = statusCode

  override def getProtocolText: String = protocolText

  override def getProtocolMinorVersion: Int = -1

  override def getProtocolMajorVersion: Int = -1

  override def getStatusText: String = statusText

  override def getProtocolName: String = protocolText

  //  override def prepareResponse(headers: HttpResponseHeaders, bodyParts: util.List[HttpResponseBodyPart]): Response = {
  //    new CacheableResponse(this, headers.asInstanceOf[CacheableHttpResponseHeaders], bodyParts.asInstanceOf[util.List[CacheableHttpResponseBodyPart]])
  //  }

  override def toString = {
    s"CacheableHttpResponseStatus(code = $statusCode, text = $statusText)"
  }

  override def getLocalAddress: SocketAddress = null

  override def getRemoteAddress: SocketAddress = null
}

// https://github.com/AsyncHttpClient/async-http-client/blob/2.0/client/src/main/java/org/asynchttpclient/HttpResponseBodyPart.java
// https://github.com/AsyncHttpClient/async-http-client/blob/2.0/client/src/main/java/org/asynchttpclient/netty/LazyResponseBodyPart.java
class CacheableHttpResponseBodyPart(chunk: Array[Byte], last: Boolean) extends HttpResponseBodyPart(last) {

  override def getBodyPartBytes: Array[Byte] = chunk

  override def getBodyByteBuffer: ByteBuffer = ByteBuffer.wrap(chunk)

  override def isLast: Boolean = super.isLast

  override def length(): Int = if (chunk != null) chunk.length else 0

  override def toString: String = {
    s"CacheableHttpResponseBodyPart(last = $last, chunk size = ${chunk.size})"
  }
}
