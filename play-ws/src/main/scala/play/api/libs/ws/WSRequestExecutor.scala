package play.api.libs.ws

import scala.concurrent.Future

trait WSRequestExecutor {
  def execute(request: WSRequest): Future[WSResponse]
}
