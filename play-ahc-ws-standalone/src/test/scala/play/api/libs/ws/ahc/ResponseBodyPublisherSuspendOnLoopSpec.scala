/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.specs2.mutable.Specification
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart
import play.shaded.ahc.org.asynchttpclient.ResponseBodyControl

import scala.jdk.CollectionConverters._

/**
 * The lost-resume fix relies on `ResponseBodyPublisher` calling `ResponseBodyControl.suspend()` only on the channel
 * event loop: AHC applies a call made on the loop inline and queues a call made elsewhere, so an off-loop suspend could
 * take effect after a newer inline resume and stall the stream. Each step below runs to completion, including the loop
 * tasks it queues, before the next one starts, so these checks do not depend on thread timing.
 */
class ResponseBodyPublisherSuspendOnLoopSpec extends Specification {
  import ResponseBodyPublisherSuspendOnLoopSpec._

  sequential

  "ResponseBodyPublisher" should {
    "suspend only on the event loop while demand arrives from other threads" in withRig { r =>
      r.onLoop(r.offer(2))
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1))
      r.onLoop(r.offer(1))
      r.onOther(r.subscriber.request(5))
      r.onLoop(r.finish())
      r.subscriber.received.get() must beEqualTo(4L)
      r.subscriber.completed must beTrue
      r.offLoopSuspends must beEmpty
    }

    "suspend only on the event loop when onNext requests more from another thread" in withRig { r =>
      r.subscriber.requestFromOnNext = Some(r.helper)
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1))
      r.onLoop(r.offer(3))
      r.control.suspended must beFalse
      r.onLoop(r.finish())
      r.subscriber.received.get() must beEqualTo(4L)
      r.subscriber.completed must beTrue
      r.offLoopSuspends must beEmpty
    }

    "deliver buffered parts when onNext requests more from another thread" in withRig { r =>
      r.onLoop(r.offer(2))
      r.subscriber.requestFromOnNext = Some(r.helper)
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1)) // drains on fake-pekko; onNext's request(1) arrives from fake-helper
      r.subscriber.received.get() must beEqualTo(2L)
      r.publisher.bufferedBodyParts must beEqualTo(0)
      r.control.suspended must beFalse
      r.offLoopSuspends must beEmpty
    }

    "suspend only on the event loop when the response fails off the loop" in withRig { r =>
      r.onLoop(r.offer(2))
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1))
      r.onTimer(r.publisher.fail(new TimeoutException("request timeout")))
      r.subscriber.failure must beAnInstanceOf[TimeoutException]
      r.offLoopSuspends must beEmpty
    }

    "suspend only on the event loop when the subscriber cancels off the loop" in withRig { r =>
      r.onLoop(r.offer(2))
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1))
      r.onOther(r.subscriber.cancel())
      r.control.cancelled must beTrue
      r.offLoopSuspends must beEmpty
    }

    "stop reads while nothing is requested and resume them once demand drains the queue" in withRig { r =>
      r.onOther(r.publisher.subscribe(r.subscriber))
      r.onOther(r.subscriber.request(1))
      r.control.suspended must beFalse
      r.onLoop(r.offer(1))
      r.control.suspended must beTrue
      r.onOther(r.subscriber.request(3))
      r.control.suspended must beFalse
      r.onLoop(r.offer(1))
      r.control.suspended must beFalse
      r.onLoop(r.offer(3))
      r.control.suspended must beTrue
      r.publisher.bufferedBodyParts must beEqualTo(1)
      r.offLoopSuspends must beEmpty
    }
  }

  private def withRig[T](test: Rig => T): T = {
    val rig = new Rig
    try test(rig)
    finally rig.shutdown()
  }
}

object ResponseBodyPublisherSuspendOnLoopSpec {

  final class Rig {
    @volatile private var loopThread: Thread = _
    val loop: ExecutorService                = Executors.newSingleThreadExecutor { (task: Runnable) =>
      val thread = new Thread(task, "fake-event-loop")
      thread.setDaemon(true)
      loopThread = thread
      thread
    }
    val other: ExecutorService  = named("fake-pekko")
    val timer: ExecutorService  = named("fake-ahc-timer")
    val helper: ExecutorService = named("fake-helper")

    private val offLoop                           = new ConcurrentLinkedQueue[String]()
    def offLoopSuspends: List[String]             = offLoop.asScala.toList
    def inEventLoop: Boolean                      = Thread.currentThread() eq loopThread
    def recordOffLoopSuspend(where: String): Unit = offLoop.add(where)

    val control                = new LoopControl(this)
    private[ahc] val publisher = call(loop)(new ResponseBodyPublisher(control)) // onResponseBodyStart runs on the loop
    val subscriber             = new TestSubscriber

    def onLoop(body: => Unit): Unit  = { call(loop)(body); quiesce() }
    def onOther(body: => Unit): Unit = { call(other)(body); quiesce() }
    def onTimer(body: => Unit): Unit = { call(timer)(body); quiesce() }

    /** Delivers `n` body parts, as AHC's `onBodyPartReceived` does on the event loop. */
    def offer(n: Int): Unit = (1 to n).foreach(_ => publisher.offer(part(last = false)))

    /** Delivers the last part and completes, as AHC does: the control is deactivated before `onCompleted`. */
    def finish(): Unit = {
      publisher.offer(part(last = true))
      control.deactivate()
      publisher.complete()
    }

    /** Drains this fake control's queued loop actions; it does not recursively drain an arbitrary executor. */
    private def quiesce(): Unit = { call(loop)(()); call(loop)(()) }

    private def part(last: Boolean) = new StreamedHttpResponseBodyPart(Array[Byte](1), last)

    private def call[T](executor: ExecutorService)(body: => T): T =
      executor.submit(new Callable[T] { def call(): T = body }).get(5, TimeUnit.SECONDS)

    private def named(name: String): ExecutorService = Executors.newSingleThreadExecutor { (task: Runnable) =>
      val thread = new Thread(task, name)
      thread.setDaemon(true)
      thread
    }

    def shutdown(): Unit = Seq(loop, other, timer, helper).foreach(_.shutdownNow())
  }

  /** Models AHC 3.0.14 `NettyResponseBodyControl`: inline on the event loop, queued to it otherwise. */
  final class LoopControl(rig: Rig) extends ResponseBodyControl {
    @volatile var active    = true
    @volatile var suspended = false
    @volatile var cancelled = false

    private def execute(task: => Unit): Unit =
      if (rig.inEventLoop) task else rig.loop.execute(() => task)

    override def suspend(): Unit = {
      if (!rig.inEventLoop) rig.recordOffLoopSuspend(Thread.currentThread().getName)
      execute(if (active) suspended = true)
    }
    override def resume(): Unit = execute(if (active && suspended) suspended = false)
    override def cancel(): Unit = execute(if (active) { deactivate(); cancelled = true })
    def deactivate(): Unit      = { active = false; suspended = false }
  }

  final class TestSubscriber extends Subscriber[HttpResponseBodyPart] {
    @volatile private var subscription: Subscription         = _
    @volatile var completed                                  = false
    @volatile var failure: Throwable                         = _
    @volatile var requestFromOnNext: Option[ExecutorService] = None
    val received                                             = new AtomicLong()

    def request(n: Long): Unit = subscription.request(n)
    def cancel(): Unit         = subscription.cancel()

    override def onSubscribe(s: Subscription): Unit       = subscription = s
    override def onNext(part: HttpResponseBodyPart): Unit = {
      received.incrementAndGet()
      // Like a Pekko stage: request more from another thread, here waiting until that request() has returned.
      requestFromOnNext.foreach(executor =>
        executor.submit(new Runnable { def run(): Unit = request(1) }).get(5, TimeUnit.SECONDS)
      )
    }
    override def onError(t: Throwable): Unit = failure = t
    override def onComplete(): Unit          = completed = true
  }
}
