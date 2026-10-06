/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko.Done
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import play.shaded.ahc.io.netty.buffer.ByteBuf
import play.shaded.ahc.io.netty.buffer.Unpooled
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.io.netty.util.concurrent.EventExecutor
import play.shaded.ahc.io.netty.util.internal.ThreadExecutorMap
import play.shaded.ahc.org.asynchttpclient.AsyncHandler
import play.shaded.ahc.org.asynchttpclient.AsyncHandler.State
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus
import play.shaded.ahc.org.asynchttpclient.ResponseBodyControl

import scala.concurrent.Promise
import scala.util.control.NonFatal

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
) extends AsyncHandler[Unit]
    with AhcUtilities {
  private var state                                          = StreamedState()
  @volatile private var bodyPublisher: ResponseBodyPublisher = _

  override def onStatusReceived(status: HttpResponseStatus): State = {
    if (state.publisher != EmptyPublisher) State.ABORT
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
    if (state.publisher != EmptyPublisher) State.ABORT
    else {
      state = state.copy(responseHeaders = headersToMap(h))
      State.CONTINUE
    }
  }

  override def onResponseBodyStart(control: ResponseBodyControl): State = {
    if (bodyPublisher != null || state.publisher != EmptyPublisher) State.ABORT
    else {
      bodyPublisher = new ResponseBodyPublisher(control)
      state = state.copy(publisher = bodyPublisher)
      streamStarted.trySuccess(f.apply(state))
      State.CONTINUE
    }
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): State = {
    if (bodyPublisher == null) State.ABORT
    else {
      if (bodyPart.length() > 0) {
        bodyPublisher.offer(new StreamedHttpResponseBodyPart(bodyPart.getBodyPartBytes, bodyPart.isLast))
      }
      State.CONTINUE
    }
  }

  override def onCompleted(): Unit = {
    if (bodyPublisher == null) {
      streamStarted.trySuccess(f.apply(state.copy(publisher = EmptyPublisher)))
    } else {
      bodyPublisher.complete()
    }
    streamDone.trySuccess(Done)
  }

  override def onThrowable(t: Throwable): Unit = {
    if (bodyPublisher != null) {
      bodyPublisher.fail(t)
    }
    streamStarted.tryFailure(t)
    streamDone.tryFailure(t)
  }
}

private final class StreamedHttpResponseBodyPart(bytes: Array[Byte], last: Boolean) extends HttpResponseBodyPart(last) {
  override def getBodyPartBytes: Array[Byte] = bytes
  override def getBodyByteBuffer: ByteBuffer = ByteBuffer.wrap(bytes)
  override def getBodyByteBuf: ByteBuf       = Unpooled.wrappedBuffer(bytes)
  override def length(): Int                 = bytes.length
}

private final class ResponseBodyPublisher(control: ResponseBodyControl) extends Publisher[HttpResponseBodyPart] {
  private val subscriber                   = new AtomicReference[Subscriber[? >: HttpResponseBodyPart]]()
  private val subscribed                   = new AtomicBoolean()
  private val subscriberReady              = new AtomicBoolean()
  private val queue                        = new ConcurrentLinkedQueue[HttpResponseBodyPart]()
  private val requested                    = new AtomicLong()
  private val work                         = new AtomicInteger()
  private val cancelled                    = new AtomicBoolean()
  private val terminated                   = new AtomicBoolean()
  private val stateLock                    = new AnyRef
  private val resumeCheckPending           = new AtomicBoolean()
  @volatile private var failure: Throwable = _

  // AHC 3 creates the control and starts the response body on the channel event loop. ThreadExecutorMap is internal
  // to Netty, but Play WS shades a fixed Netty version.
  private val eventLoop: EventExecutor = ThreadExecutorMap.currentExecutor()
  control.suspend()

  override def subscribe(nextSubscriber: Subscriber[? >: HttpResponseBodyPart]): Unit = {
    if (nextSubscriber == null) {
      throw new NullPointerException("Subscriber must not be null, rule 1.9")
    }

    if (subscribed.compareAndSet(false, true)) {
      subscriber.set(nextSubscriber)
      try {
        nextSubscriber.onSubscribe(BodySubscription)
      } catch {
        case NonFatal(_) =>
          cancel()
          return
      }
      subscriberReady.set(true)
      drain()
    } else {
      nextSubscriber.onSubscribe(RejectedSubscription)
      nextSubscriber.onError(new IllegalStateException("Only one subscriber is supported"))
    }
  }

  def offer(bodyPart: HttpResponseBodyPart): Unit = {
    val accepted = stateLock.synchronized {
      if (!cancelled.get() && !terminated.get()) {
        queue.offer(bodyPart)
        true
      } else {
        false
      }
    }
    if (accepted) {
      // AHC delivers body parts on that event loop. Suspending from drain() instead could queue an off-loop
      // command after a newer inline resume and leave a demanded response stalled.
      control.suspend()
      drain()
    }
  }

  def complete(): Unit = {
    val changed = stateLock.synchronized {
      !cancelled.get() && terminated.compareAndSet(false, true)
    }
    if (changed) {
      drain()
    }
  }

  def fail(t: Throwable): Unit = {
    fail(t, cancelTransport = false)
  }

  private[ahc] def bufferedBodyParts: Int = queue.size()

  private def drain(): Unit = {
    if (work.getAndIncrement() != 0) {
      return
    }

    var missed = 1
    while (missed != 0) {
      val currentSubscriber = subscriber.get()
      if (subscriberReady.get() && currentSubscriber != null && !cancelled.get()) {
        val cause = failure
        if (cause != null) {
          signalError(currentSubscriber, cause)
        } else {
          var emitted = 0L
          val demand  = requested.get()
          var next    = if (!cancelled.get() && emitted != demand) queue.poll() else null

          while (emitted != demand && next != null && !cancelled.get()) {
            try {
              currentSubscriber.onNext(next)
            } catch {
              case NonFatal(_) =>
                cancel()
            }
            emitted += 1
            next = if (emitted != demand) queue.poll() else null
          }

          if (emitted != 0 && demand != Long.MaxValue) {
            requested.addAndGet(-emitted)
          }

          if (!cancelled.get()) {
            val terminalCause = failure
            if (terminalCause != null) {
              signalError(currentSubscriber, terminalCause)
            } else if (terminated.get() && queue.isEmpty) {
              signalComplete(currentSubscriber)
            } else if (requested.get() > 0 && queue.isEmpty) {
              resumeReads()
            }
          }
        }
      }

      missed = work.addAndGet(-missed)
    }
  }

  /**
   * Resumes transport reads for unmet demand, from the channel event loop whenever that loop is known.
   *
   * AHC 3.0.14 applies a control call made on the event loop inline, but queues a call made on another thread and
   * applies it later without checking again. A drain() on a subscriber thread can find the queue empty while the loop
   * is still decoding a read. A resume() queued then only runs after the loop has buffered that read's parts, when
   * the demand may already be met, and lets one more socket read through with no demand left. For a compressed body,
   * that read can inflate to tens of MiB.
   *
   * So a drain() outside the loop schedules a single drain() on it instead, which resumes inline only if demand is
   * still unmet. AHC delivers body parts, which offer() buffers, only on that loop, and running the check as a drain()
   * pass keeps another thread from delivering the last parts between the check and resume(). Without a known loop, for
   * example when the publisher was not created on a Netty event loop, resume directly.
   *
   * This indirection can be dropped once a future AHC release defines the order in which control calls from different
   * threads take effect, or offers a resume that is evaluated on the event loop.
   */
  private def resumeReads(): Unit = {
    if (eventLoop == null || eventLoop.inEventLoop()) {
      control.resume()
    } else if (resumeCheckPending.compareAndSet(false, true)) {
      try {
        eventLoop.execute(new Runnable {
          override def run(): Unit = {
            // Clear the flag before draining, so that unmet demand found by a concurrent drain() can schedule another
            // check. Clearing it afterwards could drop that check and leave reads suspended.
            resumeCheckPending.set(false)
            drain()
          }
        })
      } catch {
        case _: RejectedExecutionException => () // The event loop is shutting down, so there is nothing to resume.
      }
    }
  }

  private def cancel(): Unit = {
    val changed = stateLock.synchronized {
      if (cancelled.compareAndSet(false, true)) {
        queue.clear()
        true
      } else {
        false
      }
    }
    if (changed) {
      subscriber.set(null)
      control.cancel()
    }
  }

  private def fail(t: Throwable, cancelTransport: Boolean): Unit = {
    if (t == null) {
      throw new NullPointerException("Failure must not be null")
    }

    val changed = stateLock.synchronized {
      if (!cancelled.get() && failure == null) {
        failure = t
        terminated.set(true)
        queue.clear()
        true
      } else {
        false
      }
    }
    if (changed) {
      if (cancelTransport) {
        control.cancel()
      }
      drain()
    }
  }

  private def signalError(
      currentSubscriber: Subscriber[? >: HttpResponseBodyPart],
      cause: Throwable
  ): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      queue.clear()
      try {
        currentSubscriber.onError(cause)
      } catch {
        case NonFatal(_) => ()
      } finally {
        subscriber.compareAndSet(currentSubscriber, null)
      }
    }
  }

  private def signalComplete(currentSubscriber: Subscriber[? >: HttpResponseBodyPart]): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      try {
        currentSubscriber.onComplete()
      } catch {
        case NonFatal(_) => ()
      } finally {
        subscriber.compareAndSet(currentSubscriber, null)
      }
    }
  }

  private object BodySubscription extends Subscription {
    override def request(elements: Long): Unit = {
      if (elements <= 0) {
        fail(new IllegalArgumentException("Demand must be greater than zero, rule 3.9"), cancelTransport = true)
      } else {
        addDemand(elements)
        drain()
      }
    }

    override def cancel(): Unit = ResponseBodyPublisher.this.cancel()
  }

  private def addDemand(elements: Long): Unit = {
    var added = false
    while (!added) {
      val current = requested.get()
      var updated = current + elements
      if (updated < 0) updated = Long.MaxValue
      added = requested.compareAndSet(current, updated)
    }
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

private case object RejectedSubscription extends Subscription {
  override def request(elements: Long): Unit = ()
  override def cancel(): Unit                = ()
}
