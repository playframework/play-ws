/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.akkahttp

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.coding.{ Deflate, Gzip, NoCoding }
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.Materializer
import play.api.libs.ws._

import scala.collection.immutable
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.{ Future, TimeoutException }
import scala.concurrent.duration.{ Duration, FiniteDuration }

object StandaloneAkkaHttpWSRequest {
  def apply(url: String)(implicit sys: ActorSystem, mat: Materializer, ctx: HttpsConnectionContext, config: WSClientConfig): StandaloneAkkaHttpWSRequest = {

    val requestTransformations = Seq(
      (req: HttpRequest) => config.userAgent.fold(req)(ua => req.withHeaders(`User-Agent`(ua))),
      (req: HttpRequest) => if (!config.compressionEnabled) req else req.withHeaders(`Accept-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate))
    )

    new StandaloneAkkaHttpWSRequest(
      requestTransformations.foldLeft(HttpRequest().withUri(Uri.parseAbsolute(url)))((req, f) => f(req)),
      Seq.empty, Duration.Inf
    )
  }
}

final class StandaloneAkkaHttpWSRequest private (
    val request: HttpRequest,
    val filters: Seq[WSRequestFilter],
    val timeout: Duration
)(implicit
    val sys: ActorSystem,
    val mat: Materializer,
    val ctx: HttpsConnectionContext,
    val config: WSClientConfig) extends StandaloneWSRequest {

  override type Self = StandaloneWSRequest
  override type Response = StandaloneWSResponse

  // FIXME make configurable
  final val MaxRedirects = 5

  /**
   * The base URL for this request
   */
  override def url: String = request.uri.toString

  /**
   * The URI for this request
   */
  override def uri: URI =
    // TODO convert directly instead of via string
    new URI(request.uri.toString)

  /**
   * The content type for this request, if any is defined.
   */
  override def contentType: Option[String] = request.entity.contentType match {
    case ContentTypes.NoContentType => None
    case contentType => Some(contentType.toString)
  }

  /**
   * The method for this request
   */
  override def method: String = request.method.value

  /**
   * The body of this request
   */
  override def body: WSBody = request.entity match {
    case HttpEntity.Empty => EmptyBody
    case HttpEntity.Strict(_, data) => InMemoryBody(data)
    case e: HttpEntity.Chunked => SourceBody(e.dataBytes)
    case other => throw new IllegalArgumentException(s"Unknown request body type [${request.entity}]")
  }

  /**
   * The headers for this request
   */
  override def headers: Map[String, Seq[String]] =
    request.headers
      .map(h => HttpHeader.parse(h.name().toLowerCase, h.value()))
      .collect {
        case ParsingResult.Ok(header, _) => header
      }
      .groupBy(_.name)
      .mapValues(_.map(_.value()))

  /**
   * The query string for this request
   */
  override def queryString: Map[String, Seq[String]] = request.uri.query().toMultiMap

  /**
   * The cookies for this request
   */
  override def cookies: Seq[WSCookie] =
    request.cookies.map(c => DefaultWSCookie(c.name, c.value))

  /**
   * A calculator of the signature for this request
   */
  override def calc: Option[WSSignatureCalculator] =
    // FIXME https://github.com/playframework/play-ws/issues/207
    None

  /**
   * The authentication this request should use
   */
  override def auth: Option[(String, String, WSAuthScheme)] =
    request.header[Authorization].map(_.credentials).collect {
      case BasicHttpCredentials(username, password) => (username, password, WSAuthScheme.BASIC)
      case other => throw new IllegalArgumentException(s"Authorization [$other] not yet supported")
    }

  /**
   * Whether this request should follow redirects
   */
  override def followRedirects: Option[Boolean] =
    Some(config.followRedirects)

  /**
   * The timeout for the request
   */
  override def requestTimeout: Option[Int] = timeout match {
    case duration: FiniteDuration => Some(duration.toMillis.toInt)
    case duration: Infinite => None
  }

  /**
   * The virtual host this request will use
   */
  override def virtualHost: Option[String] = request.header[Host].map(h => h.host.address + ":" + h.port)

  /**
   * The proxy server this request will use
   */
  override def proxyServer: Option[WSProxyServer] =
    // FIXME https://github.com/playframework/play-ws/issues/207
    None

  /**
   * sets the signature calculator for the request
   *
   * @param calc the signature calculator
   */
  override def sign(calc: WSSignatureCalculator): Self =
    // FIXME https://github.com/playframework/play-ws/issues/207
    ???

  /**
   * sets the authentication realm
   */
  override def withAuth(username: String, password: String, scheme: WSAuthScheme): Self =
    scheme match {
      case WSAuthScheme.BASIC => copy(request = request.addHeader(Authorization(BasicHttpCredentials(username, password))))
      case authScheme => throw new IllegalArgumentException(s"Authentication scheme [$scheme] not yet supported")
    }

  /**
   * Returns this request with the given headers, discarding the existing ones.
   *
   * @param headers the headers to be used
   */
  override def withHttpHeaders(headers: (String, String)*): Self =
    copy(request = request.withHeaders(headers
      .map { case (name, value) => HttpHeader.parse(name, value) }
      .collect {
        case ParsingResult.Ok(header, _) => header
      }
      .to[immutable.Seq]
    ))

  /**
   * Returns this request with the given query string parameters, discarding the existing ones.
   *
   * @param parameters the query string parameters
   */
  override def withQueryStringParameters(parameters: (String, String)*): Self =
    copy(request = request.copy(uri = request.uri.withQuery(Uri.Query(parameters.reverse: _*))))

  /**
   * Returns this request with the given cookies, discarding the existing ones.
   *
   * @param cookies the cookies to be used
   */
  override def withCookies(cookies: WSCookie*): Self =
    copy(request = request.mapHeaders(h => h.filter {
      case c: Cookie => false
      case _ => true
    } ++ cookies.map(c => Cookie(c.name, c.value))))

  /**
   * Sets whether redirects (301, 302) should be followed automatically
   */
  override def withFollowRedirects(follow: Boolean): Self =
    copy(config = config.copy(followRedirects = follow))

  /**
   * Sets the maximum time you expect the request to take.
   * Use Duration.Inf to set an infinite request timeout.
   * Warning: a stream consumption will be interrupted when this time is reached unless Duration.Inf is set.
   */
  override def withRequestTimeout(timeout: Duration): Self =
    copy(timeout = timeout)

  /**
   * Adds a filter to the request that can transform the request for subsequent filters.
   */
  override def withRequestFilter(filter: WSRequestFilter): Self =
    copy(filters = filters :+ filter)

  /**
   * Sets the virtual host to use in this request
   */
  override def withVirtualHost(vh: String): Self =
    copy(request = request.addHeader(Host(Authority.parse(vh))))

  /**
   * Sets the proxy server to use in this request
   */
  override def withProxyServer(proxyServer: WSProxyServer): Self =
    // FIXME https://github.com/playframework/play-ws/issues/207
    ???

  /**
   * Sets the method for this request
   */
  override def withMethod(method: String): Self =
    copy(request = request.withMethod(
      HttpMethods.getForKey(method)
        .getOrElse(throw new IllegalArgumentException(s"Unknown HTTP method $method"))))

  /**
   * Sets the body for this request.
   */
  override def withBody[T: BodyWritable](body: T): Self = {
    val writable = implicitly[BodyWritable[T]]
    val contentType = ContentType.parse(writable.contentType) match {
      case Left(_) => throw new IllegalArgumentException(s"Unknown content type [${writable.contentType}]")
      case Right(ct) => ct
    }

    val requestWithEntity = request.withEntity(writable.transform(body) match {
      case InMemoryBody(bytes) => HttpEntity(contentType, bytes)
      case SourceBody(source) => HttpEntity(contentType, source)
      case EmptyBody => HttpEntity.Empty
    })

    copy(request = requestWithEntity)
  }

  /**
   * Performs a GET.
   */
  override def get(): Future[Response] =
    execute(HttpMethods.GET.value)

  /**
   * Performs a PATCH request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PATCH request
   */
  override def patch[T: BodyWritable](body: T): Future[Response] =
    withBody(body).execute(HttpMethods.PATCH.value)

  /**
   * Performs a POST request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the POST request
   */
  override def post[T: BodyWritable](body: T): Future[Response] =
    withBody(body).execute(HttpMethods.POST.value)

  /**
   * Performs a PUT request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PUT request
   */
  override def put[T: BodyWritable](body: T): Future[Response] =
    withBody(body).execute(HttpMethods.PUT.value)

  /**
   * Perform a DELETE on the request asynchronously.
   */
  override def delete(): Future[Response] =
    execute(HttpMethods.DELETE.value)

  /**
   * Perform a HEAD on the request asynchronously.
   */
  override def head(): Future[Response] =
    execute(HttpMethods.HEAD.value)

  /**
   * Perform a OPTIONS on the request asynchronously.
   */
  override def options(): Future[Response] =
    execute(HttpMethods.OPTIONS.value)

  /**
   * Executes the given HTTP method.
   *
   * @param method the HTTP method that will be executed
   * @return a future with the response for this request
   */
  override def execute(method: String): Future[Response] =
    withMethod(method).execute()

  /**
   * Execute this request
   */
  override def execute(): Future[Response] = {
    def requestToResponse(request: HttpRequest): Future[HttpResponse] =
      Http().singleRequest(request, connectionContext = ctx)

    def handleRedirects(redirectsLeft: Int, request: HttpRequest)(response: HttpResponse): Future[HttpResponse] = {
      if (redirectsLeft < 1) {
        Future.failed(new IllegalStateException("Maximum redirect reached: " + MaxRedirects))
      } else if (response.status.isRedirection() && config.followRedirects) {
        val location = response.header[Location].get.uri
        val redirectUri =
          if (location.isRelative)
            if (location.path.startsWithSlash)
              request.uri.withPath(location.path)
            else
              request.uri.withPath(request.uri.path ++ location.path)
          else location
        requestToResponse(request.withUri(redirectUri)).flatMap(handleRedirects(redirectsLeft - 1, request))(sys.dispatcher)
      } else {
        Future.successful(response)
      }
    }

    def decodeResponse(response: HttpResponse): HttpResponse = {
      val decoder = response.encoding match {
        case HttpEncodings.gzip ⇒
          Gzip
        case HttpEncodings.deflate ⇒
          Deflate
        case _ ⇒
          NoCoding
      }
      decoder.decodeMessage(response)
    }

    val akkaExecutor = WSRequestExecutor { request =>
      import sys.dispatcher
      val akkaRequest = request.asInstanceOf[StandaloneAkkaHttpWSRequest].request
      val timeoutFuture = timeout match {
        case duration: Infinite => Seq.empty[Future[StandaloneAkkaHttpWSResponse]]
        case duration: FiniteDuration =>
          Seq(akka.pattern.after(duration, sys.scheduler)(Future.failed(new TimeoutException(s"Request timeout after $duration"))))
      }
      Future.firstCompletedOf(
        timeoutFuture :+
          requestToResponse(akkaRequest)
          .flatMap(handleRedirects(MaxRedirects, akkaRequest))
          .map(decodeResponse)
          .map(StandaloneAkkaHttpWSResponse.apply)(sys.dispatcher)
      )
    }

    val execution = filters.foldRight(akkaExecutor)((filter, executor) => filter.apply(executor))

    execution(this)
  }

  /**
   * Execute this request and stream the response body.
   */
  override def stream(): Future[Response] = execute()

  private def copy(
    request: HttpRequest = request,
    filters: Seq[WSRequestFilter] = filters,
    timeout: Duration = timeout,
    config: WSClientConfig = config
  ) = new StandaloneAkkaHttpWSRequest(request, filters, timeout)(sys, mat, ctx, config)
}
