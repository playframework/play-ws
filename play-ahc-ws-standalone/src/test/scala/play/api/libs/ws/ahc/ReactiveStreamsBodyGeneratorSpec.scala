/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.specs2.mutable.Specification
import play.shaded.ahc.io.netty.buffer.ByteBuf
import play.shaded.ahc.io.netty.buffer.Unpooled
import play.shaded.ahc.org.asynchttpclient.request.body.generator.FeedListener

class ReactiveStreamsBodyGeneratorSpec extends Specification {

  "ReactiveStreamsBodyGenerator" should {
    "fail a replay clearly and close the active body" in {
      val publisher = new ManualBodyPublisher
      val generator = new ReactiveStreamsBodyGenerator(publisher, -1L)
      val body      = generator.createBody()
      val buffer    = Unpooled.buffer().writeByte(1)
      publisher.emit(buffer)

      generator.createBody() must throwA[IllegalStateException]("A streamed request body cannot be replayed")

      publisher.subscribeCount.get() must beEqualTo(1)
      publisher.subscription.cancelCount.get() must beEqualTo(1)
      buffer.refCnt() must beEqualTo(0)
      body.close()
      success
    }

    "cancel a publisher that produces a second buffer without demand" in {
      val publisher      = new ManualBodyPublisher
      val generator      = new ReactiveStreamsBodyGenerator(publisher, -1L)
      val listenerError  = new AtomicReference[Throwable]()
      val contentSignals = new AtomicInteger()
      generator.setListener(new FeedListener {
        override def onContentAdded(): Unit            = contentSignals.incrementAndGet()
        override def onError(failure: Throwable): Unit = listenerError.set(failure)
      })

      val body   = generator.createBody()
      val first  = Unpooled.buffer().writeByte(1)
      val second = Unpooled.buffer().writeByte(2)
      val target = Unpooled.buffer()
      try {
        publisher.emit(first)
        publisher.emit(second)

        publisher.subscription.cancelCount.get() must beEqualTo(1)
        second.refCnt() must beEqualTo(0)
        listenerError.get() must beAnInstanceOf[IllegalStateException]
        contentSignals.get() must beEqualTo(2)
        body.transferTo(target) must throwA[IOException]
      } finally {
        body.close()
        target.release()
      }

      first.refCnt() must beEqualTo(0)
    }
  }
}

private final class ManualBodyPublisher extends Publisher[ByteBuf] {
  val subscribeCount = new AtomicInteger()
  val subscription   = new RecordingBodySubscription

  private val subscriber = new AtomicReference[Subscriber[? >: ByteBuf]]()

  override def subscribe(value: Subscriber[? >: ByteBuf]): Unit = {
    subscribeCount.incrementAndGet()
    subscriber.set(value)
    value.onSubscribe(subscription)
  }

  def emit(buffer: ByteBuf): Unit = subscriber.get().onNext(buffer)
}

private final class RecordingBodySubscription extends Subscription {
  val cancelCount = new AtomicInteger()
  val demand      = new AtomicInteger()

  override def request(elements: Long): Unit = demand.addAndGet(elements.toInt)
  override def cancel(): Unit                = cancelCount.incrementAndGet()
}
