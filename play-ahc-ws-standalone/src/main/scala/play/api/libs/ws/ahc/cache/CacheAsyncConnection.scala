/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

import play.shaded.ahc.org.asynchttpclient.AsyncHandler
import play.shaded.ahc.org.asynchttpclient.ListenableFuture
import play.shaded.ahc.org.asynchttpclient.Request
import org.slf4j.LoggerFactory
import play.shaded.ahc.org.asynchttpclient.handler.ProgressAsyncHandler

/**
 * Calls the relevant methods on the async handler, providing it with the cached response.
 */
class AsyncCacheableConnection[T](
    asyncHandler: AsyncHandler[T],
    request: Request,
    response: CacheableResponse,
    future: ListenableFuture[T]
) extends Callable[T]
    with Debug {

  import AsyncCacheableConnection._

  override def call(): T = {
    // Because this is running directly against an executor service,
    // the usual uncaught exception handler will not apply, and so
    // any kind of logging must wrap EVERYTHING in an explicit try / catch
    // block.
    try {
      if (logger.isTraceEnabled) {
        logger.trace(s"call: request = ${debug(request)}, response =  ${debug(response)}")
      }
      var state = asyncHandler.onStatusReceived(response.status)

      if (state eq AsyncHandler.State.CONTINUE) {
        state = asyncHandler.onHeadersReceived(response.headers)
      }

      if (state eq AsyncHandler.State.CONTINUE) {
        import collection.JavaConverters._
        response.bodyParts.asScala.foreach { bodyPart =>
          asyncHandler.onBodyPartReceived(bodyPart)
        }
      }

      asyncHandler match {
        case progressAsyncHandler: ProgressAsyncHandler[_] =>
          progressAsyncHandler.onHeadersWritten()
          progressAsyncHandler.onContentWritten()
        case _ =>
      }

      val t: T = asyncHandler.onCompleted
      future.done()
      t
    } catch {
      case t: Throwable =>
        logger.error("call: ", t)
        val ex: RuntimeException = new RuntimeException
        ex.initCause(t)
        throw ex
    }
  }

  override def toString: String = {
    s"AsyncCacheableConnection(request = ${debug(request)}})"
  }
}

object AsyncCacheableConnection {
  private val logger = LoggerFactory.getLogger("play.api.libs.ws.ahc.cache.AsyncCacheableConnection")
}

/**
 * A wrapper to return a ListenableFuture.
 */
class CacheFuture[T](handler: AsyncHandler[T]) extends ListenableFuture[T] {

  private var innerFuture: java.util.concurrent.CompletableFuture[T] = _

  def setInnerFuture(future: java.util.concurrent.CompletableFuture[T]) = {
    innerFuture = future
  }

  override def isDone: Boolean = innerFuture.isDone

  override def done(): Unit = {}

  override def touch(): Unit = {}

  override def abort(t: Throwable): Unit = {
    innerFuture.completeExceptionally(t)
  }

  override def isCancelled: Boolean = {
    innerFuture.isCancelled
  }

  override def get(): T = {
    get(1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
  }

  override def get(timeout: Long, unit: TimeUnit): T = {
    innerFuture.get(timeout, unit)
  }

  override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
    innerFuture.cancel(mayInterruptIfRunning)
  }

  override def toString: String = {
    s"CacheFuture"
  }

  override def toCompletableFuture: CompletableFuture[T] = innerFuture

  override def addListener(listener: Runnable, executor: Executor): ListenableFuture[T] = {
    innerFuture.whenCompleteAsync(
      new BiConsumer[T, Throwable]() {
        override def accept(t: T, u: Throwable): Unit = listener.run()
      },
      executor
    )
    this
  }
}
