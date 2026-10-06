/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.reactivestreams.tck.PublisherVerification
import org.reactivestreams.tck.TestEnvironment
import org.specs2.mutable.Specification
import org.testng.TestListenerAdapter
import org.testng.TestNG
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart
import play.shaded.ahc.org.asynchttpclient.ResponseBodyControl

import scala.jdk.CollectionConverters._

class ResponseBodyPublisherSpec extends Specification {

  sequential

  "ResponseBodyPublisher" should {
    "never signal before onSubscribe returns" in {
      val publisher            = new ResponseBodyPublisher(NoopResponseBodyControl)
      val onSubscribeEntered   = new CountDownLatch(1)
      val releaseOnSubscribe   = new CountDownLatch(1)
      val completionReceived   = new CountDownLatch(1)
      val signals              = new ConcurrentLinkedQueue[String]()
      val unexpectedFailure    = new AtomicReference[Throwable]()
      val subscriptionExecutor = Executors.newSingleThreadExecutor()

      try {
        val subscriptionTask = subscriptionExecutor.submit(new Runnable {
          override def run(): Unit = publisher.subscribe(new Subscriber[HttpResponseBodyPart] {
            override def onSubscribe(subscription: Subscription): Unit = {
              signals.offer("onSubscribe-enter")
              onSubscribeEntered.countDown()
              releaseOnSubscribe.await(5, TimeUnit.SECONDS)
              signals.offer("onSubscribe-exit")
            }

            override def onNext(bodyPart: HttpResponseBodyPart): Unit = signals.offer("onNext")

            override def onError(failure: Throwable): Unit = {
              unexpectedFailure.set(failure)
              signals.offer("onError")
              completionReceived.countDown()
            }

            override def onComplete(): Unit = {
              signals.offer("onComplete")
              completionReceived.countDown()
            }
          })
        })

        onSubscribeEntered.await(5, TimeUnit.SECONDS) must beTrue
        publisher.complete()
        completionReceived.await(100, TimeUnit.MILLISECONDS) must beFalse

        releaseOnSubscribe.countDown()
        subscriptionTask.get(5, TimeUnit.SECONDS)
        completionReceived.await(5, TimeUnit.SECONDS) must beTrue
        unexpectedFailure.get() must beNull
        signals.asScala.toSeq must beEqualTo(Seq("onSubscribe-enter", "onSubscribe-exit", "onComplete"))
      } finally {
        releaseOnSubscribe.countDown()
        subscriptionExecutor.shutdownNow()
      }
    }

    "allow cancellation while an offered body part is suspending reads" in {
      val control      = new BlockingResponseBodyControl
      val publisher    = new ResponseBodyPublisher(control)
      val subscription = new AtomicReference[Subscription]()
      val executor     = Executors.newFixedThreadPool(2)

      publisher.subscribe(new Subscriber[HttpResponseBodyPart] {
        override def onSubscribe(value: Subscription): Unit       = subscription.set(value)
        override def onNext(bodyPart: HttpResponseBodyPart): Unit = ()
        override def onError(failure: Throwable): Unit            = ()
        override def onComplete(): Unit                           = ()
      })

      try {
        control.blockNextSuspend()
        val offerTask = executor.submit(new Runnable {
          override def run(): Unit =
            publisher.offer(new StreamedHttpResponseBodyPart(Array(1.toByte), last = false))
        })

        control.suspendEntered.await(5, TimeUnit.SECONDS) must beTrue
        val cancelTask = executor.submit(new Runnable {
          override def run(): Unit = subscription.get().cancel()
        })

        cancelTask.get(5, TimeUnit.SECONDS)
        control.releaseSuspend.countDown()
        offerTask.get(5, TimeUnit.SECONDS)

        publisher.bufferedBodyParts must beEqualTo(0)
        control.cancelCount.get() must beEqualTo(1)
      } finally {
        control.releaseSuspend.countDown()
        executor.shutdownNow()
      }
    }

    "deliver concurrent demand while an offered body part is suspending reads" in {
      val control       = new BlockingResponseBodyControl
      val publisher     = new ResponseBodyPublisher(control)
      val subscription  = new AtomicReference[Subscription]()
      val receivedCount = new AtomicInteger()
      val executor      = Executors.newFixedThreadPool(2)

      publisher.subscribe(new Subscriber[HttpResponseBodyPart] {
        override def onSubscribe(value: Subscription): Unit       = subscription.set(value)
        override def onNext(bodyPart: HttpResponseBodyPart): Unit = receivedCount.incrementAndGet()
        override def onError(failure: Throwable): Unit            = ()
        override def onComplete(): Unit                           = ()
      })

      try {
        control.blockNextSuspend()
        val offerTask = executor.submit(new Runnable {
          override def run(): Unit =
            publisher.offer(new StreamedHttpResponseBodyPart(Array(1.toByte), last = false))
        })

        control.suspendEntered.await(5, TimeUnit.SECONDS) must beTrue
        val requestTask = executor.submit(new Runnable {
          override def run(): Unit = subscription.get().request(1)
        })

        requestTask.get(5, TimeUnit.SECONDS)
        receivedCount.get() must beEqualTo(1)

        control.releaseSuspend.countDown()
        offerTask.get(5, TimeUnit.SECONDS)
        receivedCount.get() must beEqualTo(1)
      } finally {
        control.releaseSuspend.countDown()
        executor.shutdownNow()
      }
    }

    "pass the Reactive Streams publisher TCK" in {
      val listener = new TestListenerAdapter
      val testNG   = new TestNG(false)
      testNG.setUseDefaultListeners(false)
      testNG.setVerbose(0)
      testNG.setTestClasses(Array(classOf[ResponseBodyPublisherVerification]))
      testNG.addListener(listener)
      testNG.run()

      val failures = (listener.getFailedTests.asScala ++ listener.getConfigurationFailures.asScala).map { result =>
        val cause = Option(result.getThrowable).fold("") { failure =>
          s": ${failure.getClass.getName}: ${failure.getMessage}"
        }
        s"${result.getName}$cause"
      }
      failures must beEmpty
    }
  }
}

final class ResponseBodyPublisherVerification
    extends PublisherVerification[HttpResponseBodyPart](new TestEnvironment(1000L)) {

  override def createPublisher(elements: Long): Publisher[HttpResponseBodyPart] = {
    val publisher = new ResponseBodyPublisher(NoopResponseBodyControl)
    var emitted   = 0L
    while (emitted < elements) {
      emitted += 1
      publisher.offer(
        new StreamedHttpResponseBodyPart(Array((emitted & 0xff).toByte), last = emitted == elements)
      )
    }
    publisher.complete()
    publisher
  }

  override def createFailedPublisher(): Publisher[HttpResponseBodyPart] = {
    val publisher = new ResponseBodyPublisher(NoopResponseBodyControl)
    publisher.fail(new RuntimeException("expected publisher failure"))
    publisher
  }

  override def maxElementsFromPublisher(): Long = 1024L
}

private object NoopResponseBodyControl extends ResponseBodyControl {
  override def suspend(): Unit = ()
  override def resume(): Unit  = ()
  override def cancel(): Unit  = ()
}

private final class BlockingResponseBodyControl extends ResponseBodyControl {
  val suspendEntered = new CountDownLatch(1)
  val releaseSuspend = new CountDownLatch(1)
  val cancelCount    = new AtomicInteger()

  private val blockSuspend = new AtomicBoolean()

  def blockNextSuspend(): Unit = blockSuspend.set(true)

  override def suspend(): Unit = {
    if (blockSuspend.compareAndSet(true, false)) {
      suspendEntered.countDown()
      releaseSuspend.await(5, TimeUnit.SECONDS)
    }
  }

  override def resume(): Unit = ()

  override def cancel(): Unit = cancelCount.incrementAndGet()
}
