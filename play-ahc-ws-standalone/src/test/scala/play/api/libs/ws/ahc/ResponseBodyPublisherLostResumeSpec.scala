/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.specs2.mutable.Specification
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart
import play.shaded.ahc.org.asynchttpclient.ResponseBodyControl

class ResponseBodyPublisherLostResumeSpec extends Specification {

  sequential

  "ResponseBodyPublisher with an AHC-style event-loop control" should {
    "not leave transport reads suspended while demand is outstanding and nothing is buffered" in {
      val control   = new EventLoopResponseBodyControl
      val requester = Executors.newSingleThreadExecutor()
      try {
        // AHC calls onResponseBodyStart on the channel event loop, so the constructor's suspend() runs inline.
        val publisher        = control.onLoop(new ResponseBodyPublisher(control))
        val subscriber       = new DemandRecordingSubscriber
        val requested        = new AtomicBoolean()
        def request2(): Unit = // from a non-event-loop thread, like a Pekko stream
          requester.submit(new Runnable { def run(): Unit = subscriber.request(2) }).get(5, TimeUnit.SECONDS)

        // 1. The event loop is busy with a read that will deliver one body part.
        val loopBusy    = new CountDownLatch(1)
        val releaseLoop = new CountDownLatch(1)
        val read        = control.loop.submit(new Runnable {
          def run(): Unit = {
            loopBusy.countDown()
            releaseLoop.await(5, TimeUnit.SECONDS)
            publisher.offer(new StreamedHttpResponseBodyPart(Array[Byte](1), false))
          }
        })
        loopBusy.await(5, TimeUnit.SECONDS) must beTrue

        // 2. Subscribe without demand from a non-event-loop thread; its drain() runs on this thread.
        publisher.subscribe(subscriber)

        // 3. Demand arrives from another thread while the event loop holds drain()'s WIP counter inside an inline
        //    suspend() that is not offer()'s own (the 2nd inline suspend of the read), so that thread's drain()
        //    returns on the WIP check. If that hook point does not exist, issue the same demand after the read.
        control.inlineSuspends.set(0)
        control.onInlineSuspend = n => if (n == 2 && requested.compareAndSet(false, true)) request2()
        releaseLoop.countDown()
        read.get(5, TimeUnit.SECONDS)
        control.onInlineSuspend = _ => ()
        if (requested.compareAndSet(false, true)) request2()
        control.quiesce()

        // Invariant at quiescence: not terminated && demand > 0 && queue empty => transport not suspended.
        subscriber.received.get() must beEqualTo(1L)
        subscriber.outstanding must beEqualTo(1L)
        publisher.bufferedBodyParts must beEqualTo(0)
        control.suspended must beFalse

        // Equivalently, the response completes: the transport delivers the final part only while reads are on.
        control.onLoop {
          if (!control.suspended) {
            publisher.offer(new StreamedHttpResponseBodyPart(Array[Byte](2), true))
            control.deactivate() // AHC deactivates the control before onCompleted
            publisher.complete()
          }
        }
        subscriber.completed.await(1, TimeUnit.SECONDS) must beTrue
      } finally {
        requester.shutdownNow()
        control.loop.shutdownNow()
      }
    }
  }
}

/**
 * Models AHC's `NettyResponseBodyControl`: a call, including `execute`, runs inline when made on the channel event loop
 * and is queued to the event loop otherwise; `suspended` is the applied state (suspended + autoRead(false)).
 */
final class EventLoopResponseBodyControl extends ResponseBodyControl {
  private val loopThread             = new AtomicReference[Thread]()
  val loop: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
    def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "fake-event-loop")
      t.setDaemon(true)
      loopThread.set(t)
      t
    }
  })
  @volatile var active                       = true
  @volatile var suspended                    = false
  @volatile var cancelled                    = false
  @volatile var onInlineSuspend: Int => Unit = _ => ()
  @volatile var onResumed: () => Unit        = () => () // a transport would start reading here
  val inlineSuspends                         = new AtomicInteger()

  def inEventLoop: Boolean     = Thread.currentThread() eq loopThread.get()
  def onLoop[T](body: => T): T =
    if (inEventLoop) body else loop.submit(new Callable[T] { def call(): T = body }).get(10, TimeUnit.SECONDS)

  /** Waits until every task queued so far (and any queued by them) has run. */
  def quiesce(): Unit = { onLoop(()); onLoop(()) }

  private def onLoopOrQueue(task: () => Unit): Unit =
    if (inEventLoop) task()
    else
      try loop.execute(new Runnable { def run(): Unit = task() })
      catch { case _: java.util.concurrent.RejectedExecutionException => () } // as AHC: loop is gone

  override def execute(task: Runnable): Unit = onLoopOrQueue(() => task.run())

  override def suspend(): Unit = {
    val inline = inEventLoop
    onLoopOrQueue { () =>
      if (active && !suspended) suspended = true
      if (inline) onInlineSuspend(inlineSuspends.incrementAndGet())
    }
  }

  override def resume(): Unit = onLoopOrQueue { () =>
    if (active && suspended) {
      suspended = false
      onResumed()
    }
  }

  override def cancel(): Unit = onLoopOrQueue { () =>
    if (active) {
      active = false
      suspended = false
      cancelled = true
    }
  }

  /** What AHC does on the event loop when the response completes (before `onCompleted`). */
  def deactivate(): Unit = { active = false; suspended = false }
}

/** Records demand so outstanding demand can be checked at quiescence. */
class DemandRecordingSubscriber extends Subscriber[HttpResponseBodyPart] {
  private val subscription = new AtomicReference[Subscription]()
  val requestedTotal       = new AtomicLong()
  val received             = new AtomicLong()
  val completed            = new CountDownLatch(1)
  val failure              = new AtomicReference[Throwable]()

  def request(n: Long): Unit = {
    requestedTotal.getAndUpdate(r => if (r + n < 0) Long.MaxValue else r + n)
    subscription.get().request(n)
  }
  def cancel(): Unit      = subscription.get().cancel()
  def subscribed: Boolean = subscription.get() != null
  def outstanding: Long   = { val r = requestedTotal.get(); if (r == Long.MaxValue) r else r - received.get() }

  override def onSubscribe(s: Subscription): Unit       = subscription.set(s)
  override def onNext(part: HttpResponseBodyPart): Unit = received.incrementAndGet()
  override def onError(t: Throwable): Unit              = { failure.set(t); completed.countDown() }
  override def onComplete(): Unit                       = completed.countDown()
}
