package play.libs.ws

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import java.util.concurrent.CompletionStage

object FromStreamedResponse {

  def from(f: Future[play.api.libs.ws.StreamedResponse]): CompletionStage[play.libs.ws.StreamedResponse] = {
    val function = new java.util.function.Function[play.api.libs.ws.StreamedResponse, play.libs.ws.StreamedResponse] {
      override def apply(response: play.api.libs.ws.StreamedResponse): play.libs.ws.StreamedResponse =
        play.api.libs.ws.StreamedResponse.apply(response.headers, response.body)
    }
    FutureConverters.toJava(f).thenApply(function)
  }
}