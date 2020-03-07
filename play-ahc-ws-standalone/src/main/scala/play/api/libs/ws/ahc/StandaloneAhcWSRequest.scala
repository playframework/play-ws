/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.io.UnsupportedEncodingException
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws._
import play.shaded.ahc.io.netty.buffer.Unpooled
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient.Realm.AuthScheme
import play.shaded.ahc.org.asynchttpclient._
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyType
import play.shaded.ahc.org.asynchttpclient.proxy.{ ProxyServer => AHCProxyServer }
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * A Ahc WS Request.
 */
case class StandaloneAhcWSRequest(
    client: StandaloneAhcWSClient,
    url: String,
    method: String = "GET",
    body: WSBody = EmptyBody,
    headers: Map[String, Seq[String]] = TreeMap()(CaseInsensitiveOrdered),
    queryString: Map[String, Seq[String]] = Map.empty,
    cookies: Seq[WSCookie] = Seq.empty,
    calc: Option[WSSignatureCalculator] = None,
    auth: Option[(String, String, WSAuthScheme)] = None,
    followRedirects: Option[Boolean] = None,
    requestTimeout: Option[Duration] = None,
    virtualHost: Option[String] = None,
    proxyServer: Option[WSProxyServer] = None,
    disableUrlEncoding: Option[Boolean] = None,
    private val filters: Seq[WSRequestFilter] = Nil
)(implicit materializer: Materializer)
    extends StandaloneWSRequest
    with AhcUtilities
    with WSCookieConverter {
  override type Self     = StandaloneWSRequest
  override type Response = StandaloneWSResponse

  require(client != null, "A StandaloneAhcWSClient is required, but it is null")
  require(url != null, "A url is required, but it is null")

  override def contentType: Option[String] = this.headers.get(HttpHeaders.Names.CONTENT_TYPE).map(_.head)

  override lazy val uri: URI = {
    val enc = (p: String) => java.net.URLEncoder.encode(p, "utf-8")
    new java.net.URI(
      if (queryString.isEmpty) url
      else {
        val qs = (for {
          (n, vs) <- queryString
          v       <- vs
        } yield s"${enc(n)}=${enc(v)}").mkString("&")
        s"$url?$qs"
      }
    )
  }

  override def sign(calc: WSSignatureCalculator): Self = copy(calc = Some(calc))

  override def withAuth(username: String, password: String, scheme: WSAuthScheme): Self =
    copy(auth = Some((username, password, scheme)))

  override def addHttpHeaders(hdrs: (String, String)*): StandaloneWSRequest = {
    val newHeaders = buildHeaders(headers, hdrs: _*)
    copy(headers = newHeaders)
  }

  override def withHttpHeaders(hdrs: (String, String)*): Self = {
    val emptyMap   = TreeMap[String, Seq[String]]()(CaseInsensitiveOrdered)
    val newHeaders = buildHeaders(emptyMap, hdrs: _*)
    copy(headers = newHeaders)
  }

  private def buildHeaders(origHeaders: Map[String, Seq[String]], hdrs: (String, String)*): Map[String, Seq[String]] = {
    var newHeaders = hdrs
      .foldLeft(origHeaders) { (m, hdr) =>
        if (m.contains(hdr._1)) m.updated(hdr._1, m(hdr._1) :+ hdr._2)
        else m + (hdr._1 -> Seq(hdr._2))
      }

    // preserve the content type
    newHeaders = contentType match {
      case Some(ct) =>
        newHeaders.updated(HttpHeaders.Names.CONTENT_TYPE, Seq(ct))
      case None =>
        newHeaders
    }

    newHeaders
  }

  override def addQueryStringParameters(parameters: (String, String)*): StandaloneWSRequest = {
    val newQueryString = buildQueryParams(queryString, parameters: _*)
    copy(queryString = newQueryString)
  }

  override def withQueryStringParameters(parameters: (String, String)*): Self = {
    val newQueryString = buildQueryParams(Map.empty[String, Seq[String]], parameters: _*)
    copy(queryString = newQueryString)
  }

  private def buildQueryParams(orig: Map[String, Seq[String]], params: (String, String)*): Map[String, Seq[String]] = {
    params.foldLeft(orig) {
      case (m, (k, v)) => m + (k -> (v +: m.getOrElse(k, Nil)))
    }
  }

  override def withCookies(cookies: WSCookie*): StandaloneWSRequest = copy(cookies = cookies)

  override def withFollowRedirects(follow: Boolean): Self = copy(followRedirects = Some(follow))

  override def withRequestTimeout(timeout: Duration): Self = {
    timeout match {
      case Duration.Inf =>
        copy(requestTimeout = Some(timeout))
      case d =>
        val millis = d.toMillis
        require(
          millis >= 0 && millis <= Int.MaxValue,
          s"Request timeout must be between 0 and ${Int.MaxValue} milliseconds"
        )
        copy(requestTimeout = Some(timeout))
    }
  }

  /**
   * Adds a filter to the request that can transform the request for subsequent filters.
   */
  override def withRequestFilter(filter: WSRequestFilter): Self = {
    copy(filters = filters :+ filter)
  }

  override def withVirtualHost(vh: String): Self = copy(virtualHost = Some(vh))

  override def withProxyServer(proxyServer: WSProxyServer): Self = copy(proxyServer = Some(proxyServer))

  /**
   * performs a get
   */
  override def get(): Future[Response] = {
    execute("GET")
  }

  /**
   *
   */
  override def patch[T: BodyWritable](body: T): Future[Response] = {
    withBody(body).execute("PATCH")
  }

  /**
   *
   */
  override def post[T: BodyWritable](body: T): Future[Response] = {
    withBody(body).execute("POST")
  }

  /**
   *
   */
  override def put[T: BodyWritable](body: T): Future[Response] = {
    withBody(body).execute("PUT")
  }

  /**
   * Sets the body for this request.
   */
  def withBody[T: BodyWritable](body: T): Self = {
    val writable = implicitly[BodyWritable[T]]
    withBodyAndContentType(writable.transform(body), writable.contentType)
  }

  private def withBodyAndContentType(wsBody: WSBody, contentType: String): Self = {
    if (headers.contains(HttpHeaders.Names.CONTENT_TYPE)) {
      copy(body = wsBody)
    } else {
      copy(body = wsBody).addHttpHeaders(HttpHeaders.Names.CONTENT_TYPE -> contentType)
    }
  }

  /**
   * Perform a DELETE on the request asynchronously.
   */
  override def delete(): Future[Response] = {
    execute("DELETE")
  }

  /**
   * Perform a HEAD on the request asynchronously.
   */
  override def head(): Future[Response] = {
    execute("HEAD")
  }

  /**
   * Perform a OPTIONS on the request asynchronously.
   */
  override def options(): Future[Response] = {
    execute("OPTIONS")
  }

  override def execute(method: String): Future[Response] = {
    withMethod(method).execute()
  }

  override def withUrl(url: String): Self = copy(url = url)

  override def withMethod(method: String): Self = copy(method = method)

  override def execute(): Future[Response] = {
    val executor = filterWSRequestExecutor(WSRequestExecutor { request =>
      client.execute(request.asInstanceOf[StandaloneAhcWSRequest].buildRequest())
    })

    executor(this)
  }

  protected def filterWSRequestExecutor(next: WSRequestExecutor): WSRequestExecutor = {
    filters.foldRight(next)((filter, executor) => filter.apply(executor))
  }

  override def stream(): Future[Response] = {
    val executor = filterWSRequestExecutor(WSRequestExecutor { request =>
      client.executeStream(request.asInstanceOf[StandaloneAhcWSRequest].buildRequest())
    })

    executor(this)
  }

  /**
   * Returns the HTTP header given by name, using the request builder.  This may be signed,
   * so may return extra headers that were not directly input.
   */
  def requestHeader(name: String): Option[String] = {
    requestHeaders.get(name).flatMap(_.headOption)
  }

  /**
   * Returns the current headers of the request, using the request builder.  This may be signed,
   * so may return extra headers that were not directly input.
   */
  def requestHeaders: Map[String, Seq[String]] = {
    headersToMap(buildRequest().getHeaders)
  }

  /**
   * Returns the current query string parameters, using the request builder.  This may be signed,
   * so may not return the same parameters that were input.
   */
  def requestQueryParams: Map[String, Seq[String]] = {
    val params: java.util.List[Param] = buildRequest().getQueryParams
    params.asScala.toSeq.groupBy(_.getName).map(kv => kv._1 -> kv._2.map(_.getValue))
  }

  /**
   * Creates and returns an AHC request, running all operations on it.
   */
  def buildRequest(): play.shaded.ahc.org.asynchttpclient.Request = {
    // The builder has a bunch of mutable state and is VERY fiddly, so
    // should not be exposed to the outside world.

    val builder = disableUrlEncoding
      .map { disableEncodingFlag =>
        new RequestBuilder(method, disableEncodingFlag)
      }
      .getOrElse {
        new RequestBuilder(method)
      }

    // Set the URL.
    builder.setUrl(url)

    // auth
    auth.foreach { data =>
      val realm = auth(data._1, data._2, authScheme(data._3))
      builder.setRealm(realm)
    }

    // queries
    for {
      (key, values) <- queryString
      value         <- values
    } builder.addQueryParam(key, value)

    // Configuration settings on the builder, if applicable
    virtualHost.foreach(builder.setVirtualHost)
    followRedirects.foreach(builder.setFollowRedirect)
    proxyServer.foreach(p => builder.setProxyServer(createProxy(p)))
    requestTimeout.foreach {
      case d if d == Duration.Inf =>
        builder.setRequestTimeout(-1)
      case d =>
        builder.setRequestTimeout(d.toMillis.toInt)
    }

    val (builderWithBody, updatedHeaders) = body match {
      case EmptyBody => (builder, this.headers)
      case InMemoryBody(bytes) =>
        val ct: String = contentType.getOrElse("text/plain")

        val h =
          try {
            // Only parse out the form body if we are doing the signature calculation.
            if (ct.contains(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED) && calc.isDefined) {
              // If we are taking responsibility for setting the request body, we should block any
              // externally defined Content-Length field (see #5221 for the details)
              val filteredHeaders = this.headers.filterNot {
                case (k, v) => k.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH)
              }

              // extract the content type and the charset
              val charsetOption = Option(HttpUtils.extractContentTypeCharsetAttribute(ct))
              val charset = charsetOption
                .getOrElse {
                  StandardCharsets.UTF_8
                }
                .name()

              // Get the string body given the given charset...
              val stringBody = bytes.decodeString(charset)
              // The Ahc signature calculator uses request.getFormParams() for calculation,
              // so we have to parse it out and add it rather than using setBody.

              val params = for {
                (key, values) <- FormUrlEncodedParser.parse(stringBody).toSeq
                value         <- values
              } yield new Param(key, value)
              builder.setFormParams(params.asJava)
              filteredHeaders
            } else {
              builder.setBody(bytes.toArray)
              this.headers
            }
          } catch {
            case e: UnsupportedEncodingException =>
              throw new RuntimeException(e)
          }

        (builder, h)
      case SourceBody(source) =>
        // If the body has a streaming interface it should be up to the user to provide a manual Content-Length
        // else every content would be Transfer-Encoding: chunked
        // If the Content-Length is -1 Async-Http-Client sets a Transfer-Encoding: chunked
        // If the Content-Length is great than -1 Async-Http-Client will use the correct Content-Length
        val filteredHeaders = this.headers.filterNot {
          case (k, v) => k.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH)
        }
        val contentLength = this.headers
          .find { case (k, _) => k.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH) }
          .map(_._2.head.toLong)

        (
          builder.setBody(
            source.map(bs => Unpooled.wrappedBuffer(bs.toByteBuffer)).runWith(Sink.asPublisher(false)),
            contentLength.getOrElse(-1L)
          ),
          filteredHeaders
        )
    }

    // headers
    for {
      header <- updatedHeaders
      value  <- header._2
    } builder.addHeader(header._1, value)

    // Set the signature calculator.
    calc.map {
      case signatureCalculator: play.shaded.ahc.org.asynchttpclient.SignatureCalculator =>
        builderWithBody.setSignatureCalculator(signatureCalculator)
      case _ =>
        throw new IllegalStateException(
          "Unknown signature calculator found: use a class that implements SignatureCalculator"
        )
    }

    // cookies
    cookies.foreach(c => builder.addCookie(asCookie(c)))

    builderWithBody.build()
  }

  private[libs] def authScheme(scheme: WSAuthScheme): Realm.AuthScheme = scheme match {
    case WSAuthScheme.DIGEST   => Realm.AuthScheme.DIGEST
    case WSAuthScheme.BASIC    => Realm.AuthScheme.BASIC
    case WSAuthScheme.NTLM     => Realm.AuthScheme.NTLM
    case WSAuthScheme.SPNEGO   => Realm.AuthScheme.SPNEGO
    case WSAuthScheme.KERBEROS => Realm.AuthScheme.KERBEROS
    case _                     => throw new RuntimeException("Unknown scheme " + scheme)
  }

  /**
   * Add http auth headers. Defaults to HTTP Basic.
   */
  private[libs] def auth(
      username: String,
      password: String,
      scheme: Realm.AuthScheme = Realm.AuthScheme.BASIC
  ): Realm = {
    val usePreemptiveAuth = scheme match {
      case AuthScheme.DIGEST => false
      case _                 => true
    }

    new Realm.Builder(username, password)
      .setScheme(scheme)
      .setUsePreemptiveAuth(usePreemptiveAuth)
      .build()
  }

  private[libs] def createProxy(wsProxyServer: WSProxyServer): AHCProxyServer = {
    val proxyBuilder = new AHCProxyServer.Builder(wsProxyServer.host, wsProxyServer.port)
    if (wsProxyServer.principal.isDefined) {
      val realmBuilder = new Realm.Builder(wsProxyServer.principal.orNull, wsProxyServer.password.orNull)
      val scheme: Realm.AuthScheme =
        wsProxyServer.protocol.getOrElse("http").toLowerCase(java.util.Locale.ENGLISH) match {
          case "http" | "https" => Realm.AuthScheme.BASIC
          case "kerberos"       => Realm.AuthScheme.KERBEROS
          case "ntlm"           => Realm.AuthScheme.NTLM
          case "spnego"         => Realm.AuthScheme.SPNEGO
          case _                => scala.sys.error("Unrecognized protocol!")
        }
      realmBuilder.setScheme(scheme)
      wsProxyServer.encoding.foreach(enc => realmBuilder.setCharset(Charset.forName(enc)))
      wsProxyServer.ntlmDomain.foreach(realmBuilder.setNtlmDomain)
      proxyBuilder.setRealm(realmBuilder)
    }

    val proxyType = wsProxyServer.proxyType.getOrElse("http").toLowerCase(java.util.Locale.ENGLISH) match {
      case "http" =>
        ProxyType.HTTP
      case "socksv4" =>
        ProxyType.SOCKS_V4
      case "socksv5" =>
        ProxyType.SOCKS_V5
    }
    proxyBuilder.setProxyType(proxyType);

    wsProxyServer.nonProxyHosts.foreach { nonProxyHosts =>
      import scala.collection.JavaConverters._
      proxyBuilder.setNonProxyHosts(nonProxyHosts.asJava)
    }
    proxyBuilder.build()
  }

  /**
   * Returns the current URL, using the request builder.  This may be signed by OAuth, as opposed
   * to request.url.  This is an AHC specific method.
   */
  def requestUrl: String = {
    buildRequest().getUrl
  }

}
