/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

import org.apache.pekko.util.ByteString
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.api.routing.sird._
import play.shaded.ahc.org.asynchttpclient.AsyncHandler.State
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart

import scala.concurrent.Promise

class StreamedGzipBufferingSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider {

  sequential

  // 64 MiB of zeros gzip to about 64 KiB, so each socket read of the body inflates roughly a thousandfold.
  private val gzippedZeros: ByteString = {
    val bytes = new ByteArrayOutputStream()
    val gzip  = new GZIPOutputStream(bytes)
    val zeros = new Array[Byte](1024 * 1024)
    (1 to 64).foreach(_ => gzip.write(zeros))
    gzip.close()
    ByteString(bytes.toByteArray)
  }

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = {
    case GET(p"/gzip-zeros") =>
      components.defaultActionBuilder {
        Results.Ok(gzippedZeros).as("application/octet-stream").withHeaders("Content-Encoding" -> "gzip")
      }
  }

  "A streamed gzip response" should {
    "not read past the demanded socket read when demand arrives from another thread" in {
      val client = new DefaultAsyncHttpClient(new AhcConfigBuilder(AhcWSClientConfigFactory.forConfig()).build())
      try {
        // The first read, which also carries the headers, inflates to about 2 MiB; one more read to tens of MiB.
        val bufferedBeyondDemand = (1 to 3).map(_ => bufferedAfterEarlyDemand(client))
        bufferedBeyondDemand must contain(beLessThan(10L * 1024 * 1024)).forall
      } finally {
        client.close()
      }
    }
  }

  /**
   * Subscribes from another thread and requests one part before AHC decodes any body bytes, then stops requesting.
   * Returns how many inflated bytes AHC delivered to the publisher beyond those the subscriber received.
   */
  private def bufferedAfterEarlyDemand(client: AsyncHttpClient): Long = {
    val offered      = new AtomicLong()
    val received     = new AtomicLong()
    val firstPart    = new CountDownLatch(1)
    val subscription = new AtomicReference[Subscription]()
    val failure      = new AtomicReference[Throwable]()
    val subscriber   = new Subscriber[HttpResponseBodyPart] {
      override def onSubscribe(s: Subscription): Unit = {
        subscription.set(s)
        s.request(1)
      }
      override def onNext(part: HttpResponseBodyPart): Unit = {
        received.addAndGet(part.length().toLong)
        firstPart.countDown()
      }
      override def onError(t: Throwable): Unit = failure.set(t)
      override def onComplete(): Unit          = ()
    }
    val requester = Executors.newSingleThreadExecutor()
    try {
      // AHC calls this on the channel event loop from onResponseBodyStart, before it decodes the body bytes of the
      // read that carried the headers. Subscribe from another thread, like a Pekko stream, and wait until it is done.
      val subscribe: java.util.function.Function[StreamedState, StreamedState] = { state =>
        requester
          .submit(new Runnable { def run(): Unit = state.publisher.subscribe(subscriber) })
          .get(5, TimeUnit.SECONDS)
        state
      }
      val handler = new DefaultStreamedAsyncHandler[StreamedState](subscribe, Promise(), Promise()) {
        override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): State = {
          offered.addAndGet(bodyPart.length().toLong)
          super.onBodyPartReceived(bodyPart)
        }
      }
      client.prepareGet(s"http://localhost:$testServerPort/gzip-zeros").execute(handler)

      if (!firstPart.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("No body part was received", failure.get())
      }
      awaitNoMoreParts(offered)
      subscription.get().cancel()
      offered.get() - received.get()
    } finally {
      requester.shutdownNow()
    }
  }

  /** Waits until AHC has delivered no body part for 300 milliseconds. */
  private def awaitNoMoreParts(offered: AtomicLong): Unit = {
    val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    var last      = offered.get()
    var idleSince = System.nanoTime()
    while (System.nanoTime() - idleSince < TimeUnit.MILLISECONDS.toNanos(300) && System.nanoTime() < deadline) {
      Thread.sleep(25)
      val current = offered.get()
      if (current != last) {
        last = current
        idleSince = System.nanoTime()
      }
    }
  }
}
