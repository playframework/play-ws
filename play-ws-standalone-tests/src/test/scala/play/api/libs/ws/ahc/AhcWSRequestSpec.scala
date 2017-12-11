package play.api.libs.ws.ahc

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws._
import org.specs2.execute.Result
import scala.collection.JavaConverters._

class AhcWSRequestSpec extends WSRequestSpec {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val wsClient = StandaloneAhcWSClient()

  override def afterAll: Unit = {
    wsClient.close()
    system.terminate()
  }

  def withClient(block: StandaloneWSClient => Result): Result = {
    block(wsClient)
  }

  def withRequest(url: String)(block: StandaloneWSRequest => Result): Result = {
    block(StandaloneAhcWSRequest(wsClient, url))
  }

  def getQueryParameters(req: StandaloneWSRequest): Seq[(String, String)] =
    req.asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
      .getQueryParams
      .asScala
      .map(p => (p.getName, p.getValue))

  def getCookies(req: StandaloneWSRequest): Seq[WSCookie] =
    req.asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
      .getCookies
      .asScala
      .map(c => DefaultWSCookie(c.getName, c.getValue))

  def getHeaders(req: StandaloneWSRequest): Map[String, Seq[String]] = {
    val headers = req.asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
      .getHeaders
    headers.names.asScala.map(n => n -> headers.getAll(n).asScala).toMap
  }

  def getByteData(req: StandaloneWSRequest): Array[Byte] =
    req.asInstanceOf[StandaloneAhcWSRequest]
      .buildRequest()
      .getByteData
}
