/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.net.URI

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
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

class DefaultStreamedAsyncHandler[T](f: java.util.function.Function[StreamedState, T], promise: Promise[T]) extends StreamedAsyncHandler[Unit] with AhcUtilities {
  private var state = StreamedState()

  def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
    if (this.state.publisher != EmptyPublisher) State.ABORT
    else {
      this.state = state.copy(publisher = publisher)
      promise.success(f(state))
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
    promise.trySuccess(f(state.copy(publisher = EmptyPublisher)))
  }

  override def onThrowable(t: Throwable): Unit = promise.tryFailure(t)
}

private case object EmptyPublisher extends Publisher[HttpResponseBodyPart] {
  def subscribe(s: Subscriber[_ >: HttpResponseBodyPart]): Unit = {
    if (s eq null) throw new NullPointerException("Subscriber must not be null, rule 1.9")
    s.onSubscribe(CancelledSubscription)
    s.onComplete()
  }
  private case object CancelledSubscription extends Subscription {
    override def request(elements: Long): Unit = ()
    override def cancel(): Unit = ()
  }
}
