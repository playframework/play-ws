package play.api.libs.ws.ahc

import play.shaded.ahc.org.asynchttpclient.{ Request, Response => AHCResponse }

import scala.concurrent.{ Future, Promise }

object AhcEngine {

  private[libs] def execute(client: StandaloneAhcWSClient, request: Request): Future[StandaloneAhcWSResponse] = {
    import play.shaded.ahc.org.asynchttpclient.AsyncCompletionHandler
    val result = Promise[StandaloneAhcWSResponse]()

    client.executeRequest(request, new AsyncCompletionHandler[AHCResponse]() {
      override def onCompleted(response: AHCResponse): AHCResponse = {
        result.success(StandaloneAhcWSResponse(response))
        response
      }

      override def onThrowable(t: Throwable): Unit = {
        result.failure(t)
      }
    })
    result.future
  }
}
