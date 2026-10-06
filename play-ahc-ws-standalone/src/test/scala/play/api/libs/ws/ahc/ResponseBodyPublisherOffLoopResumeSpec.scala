/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import org.specs2.mutable.Specification
import play.shaded.ahc.io.netty.util.concurrent.DefaultThreadFactory
import play.shaded.ahc.io.netty.util.concurrent.EventExecutor
import play.shaded.ahc.io.netty.util.concurrent.SingleThreadEventExecutor
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart
import play.shaded.ahc.org.asynchttpclient.ResponseBodyControl

import scala.jdk.CollectionConverters._

/**
 * AHC applies a `ResponseBodyControl` call made on the channel event loop inline, but queues one made on any other
 * thread and applies it later without checking again. These examples construct the publisher on a real Netty event
 * executor, as AHC does, and request from the test thread while that executor is busy with a read.
 */
class ResponseBodyPublisherOffLoopResumeSpec extends Specification {
  import ResponseBodyPublisherOffLoopResumeSpec._

  sequential

  "ResponseBodyPublisher on a Netty event loop" should {
    "not resume reads for demand that the read in progress satisfies" in withRig { r =>
      r.whileLoopReads(parts = 3) {
        r.publisher.subscribe(r.subscriber)
        r.subscriber.request(1) // nothing is buffered yet
      }
      r.quiesce()

      r.subscriber.received.get() must beEqualTo(1L)
      r.publisher.bufferedBodyParts must beEqualTo(2)
      r.control.suspended must beTrue // a stale resume would let the next socket read through
      r.control.offLoopResumes must beEmpty
    }

    "resume reads once demand from another thread drains the queue" in withRig { r =>
      r.whileLoopReads(parts = 3) {
        r.publisher.subscribe(r.subscriber)
        r.subscriber.request(1)
      }
      r.quiesce()
      r.subscriber.request(3)
      r.quiesce()

      r.subscriber.received.get() must beEqualTo(3L)
      r.publisher.bufferedBodyParts must beEqualTo(0)
      r.control.suspended must beFalse
      r.control.offLoopResumes must beEmpty
    }

    "resume reads once for demand that arrives from another thread while the loop is busy" in withRig { r =>
      r.whileLoopReads(parts = 0) {
        r.publisher.subscribe(r.subscriber)
        (1 to 3).foreach(_ => r.subscriber.request(1))
      }
      r.quiesce()

      r.control.suspended must beFalse
      r.control.resumeCalls.get() must beEqualTo(1)
      r.control.offLoopResumes must beEmpty
    }

    "not resume reads when its check runs while another thread delivers the last demanded part" in withRig { r =>
      val released   = new CountDownLatch(1)
      val offered    = new CountDownLatch(1)
      val delivering = new CountDownLatch(1)
      val subscriber = new DemandRecordingSubscriber {
        override def onNext(part: HttpResponseBodyPart): Unit = {
          super.onNext(part)
          if (!r.loop.inEventLoop()) {
            delivering.countDown()
            r.quiesce() // the queued check runs before this drain has counted the part against demand
          }
        }
      }
      r.loop.submit(new Runnable {
        def run(): Unit = {
          released.await(5, TimeUnit.SECONDS)
          r.publisher.offer(new StreamedHttpResponseBodyPart(Array[Byte](1), false))
          offered.countDown()
          delivering.await(5, TimeUnit.SECONDS)
        }
      })
      // Once the publisher queues its check, the loop offers a part that this thread's drain then delivers.
      r.loop.afterOffLoopExecute.set(() => { released.countDown(); offered.await(5, TimeUnit.SECONDS) })
      r.publisher.subscribe(subscriber)
      subscriber.request(1)
      r.quiesce()

      subscriber.received.get() must beEqualTo(1L)
      r.publisher.bufferedBodyParts must beEqualTo(0)
      r.control.suspended must beTrue
    }
  }

  private def withRig[T](test: Rig => T): T = {
    val rig = new Rig
    try test(rig)
    finally rig.shutdown()
  }
}

object ResponseBodyPublisherOffLoopResumeSpec {

  final class Rig {
    val loop    = new HookedLoop
    val control = new LoopControl(loop)
    // AHC calls onResponseBodyStart, and so this constructor, on the channel event loop.
    private[ahc] val publisher = onLoop(new ResponseBodyPublisher(control))
    val subscriber             = new DemandRecordingSubscriber

    /** Runs `offLoop` on this thread while the loop is inside a read, which then delivers `parts` body parts. */
    def whileLoopReads(parts: Int)(offLoop: => Unit): Unit = {
      val reading = new CountDownLatch(1)
      val proceed = new CountDownLatch(1)
      val read    = loop.submit(new Runnable {
        def run(): Unit = {
          reading.countDown()
          proceed.await(5, TimeUnit.SECONDS)
          (1 to parts).foreach(_ => publisher.offer(new StreamedHttpResponseBodyPart(Array[Byte](1), false)))
        }
      })
      if (!reading.await(5, TimeUnit.SECONDS)) throw new AssertionError("The event loop did not start the read")
      try offLoop
      finally proceed.countDown()
      read.get(5, TimeUnit.SECONDS)
    }

    /** Waits until the tasks queued on the loop so far, and any queued by them, have run. */
    def quiesce(): Unit = { onLoop(()); onLoop(()) }

    def shutdown(): Unit = loop.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS)

    private def onLoop[T](body: => T): T =
      loop.submit(new Callable[T] { def call(): T = body }).get(5, TimeUnit.SECONDS)
  }

  /** A Netty event executor that runs a one-shot hook on the calling thread after queuing a task from another thread. */
  final class HookedLoop extends SingleThreadEventExecutor(null, new DefaultThreadFactory("hooked-loop"), true) {
    val afterOffLoopExecute = new AtomicReference[Runnable]()

    override def execute(task: Runnable): Unit = {
      super.execute(task)
      if (!inEventLoop()) Option(afterOffLoopExecute.getAndSet(null)).foreach(_.run())
    }

    protected override def run(): Unit = {
      var shutdown = false
      while (!shutdown) {
        val task = takeTask()
        if (task != null) {
          task.run()
          updateLastExecutionTime()
        }
        shutdown = confirmShutdown()
      }
    }
  }

  /** Models AHC 3.0.14 `NettyResponseBodyControl`: inline on the event loop, queued to it otherwise. */
  final class LoopControl(loop: EventExecutor) extends ResponseBodyControl {
    @volatile var active    = true
    @volatile var suspended = false
    val resumeCalls         = new AtomicInteger()
    private val offLoop     = new ConcurrentLinkedQueue[String]()

    def offLoopResumes: List[String] = offLoop.asScala.toList

    private def execute(task: => Unit): Unit =
      if (loop.inEventLoop()) task else loop.execute(new Runnable { def run(): Unit = task })

    override def suspend(): Unit = execute(if (active) suspended = true)
    override def resume(): Unit  = {
      resumeCalls.incrementAndGet()
      if (!loop.inEventLoop()) offLoop.add(Thread.currentThread().getName)
      execute(if (active && suspended) suspended = false)
    }
    override def cancel(): Unit = execute(if (active) { active = false; suspended = false })
  }
}
