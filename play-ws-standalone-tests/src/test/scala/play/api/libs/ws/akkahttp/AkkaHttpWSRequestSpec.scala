package play.api.libs.ws.akkahttp

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.{ StandaloneWSClient, StandaloneWSRequest, WSCookie, WSRequestSpec }
import org.specs2.execute.Result
import scala.concurrent.duration._

import scala.concurrent.Await

class AkkaHttpWSRequestSpec extends WSRequestSpec {

  implicit val system = ActorSystem()
  implicit val materi = ActorMaterializer()
  val wsClient = StandaloneAkkaHttpWSClient()

  override def afterAll: Unit = {
  }

  def withClient(block: StandaloneWSClient => Result): Result = {
    block(wsClient)
  }

  def withRequest(url: String)(block: StandaloneWSRequest => Result): Result = {
    block(StandaloneAkkaHttpWSRequest(url))
  }

  def getQueryParameters(req: StandaloneWSRequest): Seq[(String, String)] =
    req.queryString.iterator.flatMap {
      case (key, values) => values.map(value => (key, value))
    }.toSeq

  def getCookies(req: StandaloneWSRequest): Seq[WSCookie] =
    req.cookies

  def getHeaders(req: StandaloneWSRequest): Map[String, Seq[String]] =
    req.headers

  def getByteData(req: StandaloneWSRequest): Array[Byte] = {
    val timeout = 100.millis
    Await.result(
      req.asInstanceOf[StandaloneAkkaHttpWSRequest]
        .request.entity.toStrict(timeout), timeout).data.toArray
  }
}
