package play.api.libs.ws.akkahttp

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.Materializer
import play.api.libs.ws._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object StandaloneAkkaHttpWSRequest {
  def apply(url: String)(implicit sys: ActorSystem, mat: Materializer): StandaloneAkkaHttpWSRequest = new StandaloneAkkaHttpWSRequest(HttpRequest().withUri(Uri.parseAbsolute(url)), Seq.empty)
}

final class StandaloneAkkaHttpWSRequest private (
    val request: HttpRequest,
    val filters: Seq[WSRequestFilter]
)(implicit val sys: ActorSystem, val mat: Materializer) extends StandaloneWSRequest {

  override type Self = StandaloneWSRequest
  override type Response = StandaloneWSResponse

  /**
   * The base URL for this request
   */
  override def url: String = ???

  /**
   * The URI for this request
   */
  override def uri: URI =
    // TODO convert directly instead of via string
    new URI(request.uri.toString)

  /**
   * The content type for this request, if any is defined.
   */
  override def contentType: Option[String] = ???

  /**
   * The method for this request
   */
  override def method: String = ???

  /**
   * The body of this request
   */
  override def body: WSBody = ???

  /**
   * The headers for this request
   */
  override def headers: Map[String, Seq[String]] =
    request.headers
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
  override def calc: Option[WSSignatureCalculator] = ???

  /**
   * The authentication this request should use
   */
  override def auth: Option[(String, String, WSAuthScheme)] = ???

  /**
   * Whether this request should follow redirects
   */
  override def followRedirects: Option[Boolean] = ???

  /**
   * The timeout for the request
   */
  override def requestTimeout: Option[Int] = ???

  /**
   * The virtual host this request will use
   */
  override def virtualHost: Option[String] = ???

  /**
   * The proxy server this request will use
   */
  override def proxyServer: Option[WSProxyServer] = ???

  /**
   * sets the signature calculator for the request
   *
   * @param calc the signature calculator
   */
  override def sign(calc: WSSignatureCalculator): Self = ???

  /**
   * sets the authentication realm
   */
  override def withAuth(username: String, password: String, scheme: WSAuthScheme): Self = ???

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
  override def withFollowRedirects(follow: Boolean): Self = ???

  /**
   * Sets the maximum time you expect the request to take.
   * Use Duration.Inf to set an infinite request timeout.
   * Warning: a stream consumption will be interrupted when this time is reached unless Duration.Inf is set.
   */
  override def withRequestTimeout(timeout: Duration): Self = ???

  /**
   * Adds a filter to the request that can transform the request for subsequent filters.
   */
  override def withRequestFilter(filter: WSRequestFilter): Self =
    copy(filters = filters :+ filter)

  /**
   * Sets the virtual host to use in this request
   */
  override def withVirtualHost(vh: String): Self = ???

  /**
   * Sets the proxy server to use in this request
   */
  override def withProxyServer(proxyServer: WSProxyServer): Self = ???

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

    val requestWithEntity = request.withEntity(writable.transform(body) match {
      case InMemoryBody(bytes) => HttpEntity(bytes)
      case SourceBody(source) => HttpEntity(ContentType.parse(writable.contentType).right.get, source)
      case EmptyBody => HttpEntity.Empty
    })

    copy(request = requestWithEntity)
  }

  /**
   * Performs a GET.
   */
  override def get(): Future[Response] =
    execute()

  /**
   * Performs a PATCH request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PATCH request
   */
  override def patch[T: BodyWritable](body: T): Future[Response] = ???

  /**
   * Performs a POST request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the POST request
   */
  override def post[T: BodyWritable](body: T): Future[Response] = ???

  /**
   * Performs a PUT request.
   *
   * @param body the payload body submitted with this request
   * @return a future with the response for the PUT request
   */
  override def put[T: BodyWritable](body: T): Future[Response] = ???

  /**
   * Perform a DELETE on the request asynchronously.
   */
  override def delete(): Future[Response] = ???

  /**
   * Perform a HEAD on the request asynchronously.
   */
  override def head(): Future[Response] = ???

  /**
   * Perform a OPTIONS on the request asynchronously.
   */
  override def options(): Future[Response] = ???

  /**
   * Executes the given HTTP method.
   *
   * @param method the HTTP method that will be executed
   * @return a future with the response for this request
   */
  override def execute(method: String): Future[Response] = ???

  /**
   * Execute this request
   */
  override def execute(): Future[Response] = {
    val akkaExecutor = WSRequestExecutor { request =>
      val akkaRequest = request.asInstanceOf[StandaloneAkkaHttpWSRequest].request
      Http().singleRequest(akkaRequest).map(StandaloneAkkaHttpWSResponse.apply)(sys.dispatcher)
    }

    val execution = filters.foldRight(akkaExecutor)((filter, executor) => filter.apply(executor))

    execution(this)
  }

  /**
   * Execute this request and stream the response body.
   */
  override def stream(): Future[Response] = get()

  private def copy(request: HttpRequest = request, filters: Seq[WSRequestFilter] = filters) = new StandaloneAkkaHttpWSRequest(request, filters)
}
