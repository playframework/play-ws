/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.net.URI

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.reactivestreams.Publisher
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import org.apache.pekko.Done
import play.shaded.ahc.org.asynchttpclient.AsyncHandler.State
import play.shaded.ahc.org.asynchttpclient._
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler

import scala.concurrent.Promise

case class StreamedState(
    statusCode: Int = -1,
    statusText: String = "",
    uriOption: Option[URI] = None,
    responseHeaders: Map[String, scala.collection.Seq[String]] = Map.empty,
    publisher: Publisher[HttpResponseBodyPart] = EmptyPublisher
)

class DefaultStreamedAsyncHandler[T](
    f: java.util.function.Function[StreamedState, T],
    streamStarted: Promise[T],
    streamDone: Promise[Done]
) extends StreamedAsyncHandler[Unit]
    with AhcUtilities {
  private var state = StreamedState()

  def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
    if (this.state.publisher != EmptyPublisher) State.ABORT
    else {
      this.state = state.copy(publisher = publisher)
      streamStarted.success(f(state))
      State.CONTINUE
    }
  }

  override def onStatusReceived(status: HttpResponseStatus): State = {
    if (this.state.publisher != EmptyPublisher) State.ABORT
    else {
      state = state.copy(
        statusCode = status.getStatusCode,
        statusText = status.getStatusText,
        uriOption = Option(status.getUri.toJavaNetURI)
      )
      State.CONTINUE
    }
  }

  override def onHeadersReceived(h: HttpHeaders): State = {
    if (this.state.publisher != EmptyPublisher) State.ABORT
    else {
      state = state.copy(responseHeaders = headersToMap(h))
      State.CONTINUE
    }
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): State =
    throw new IllegalStateException("Should not have received bodypart")

  override def onCompleted(): Unit = {
    // EmptyPublisher can be replaces with `Source.empty` when we carry out the refactoring
    // mentioned in the `execute2` method.
    streamStarted.trySuccess(f(state.copy(publisher = EmptyPublisher)))
    streamDone.trySuccess(Done)
  }

  override def onThrowable(t: Throwable): Unit = {
    streamStarted.tryFailure(t)
    streamDone.tryFailure(t)
  }
}

private case object EmptyPublisher extends Publisher[HttpResponseBodyPart] {
  def subscribe(s: Subscriber[? >: HttpResponseBodyPart]): Unit = {
    if (s eq null)
      throw new NullPointerException("Subscriber must not be null, rule 1.9")
    s.onSubscribe(CancelledSubscription)
    s.onComplete()
  }
  private case object CancelledSubscription extends Subscription {
    override def request(elements: Long): Unit = ()
    override def cancel(): Unit                = ()
  }
}
