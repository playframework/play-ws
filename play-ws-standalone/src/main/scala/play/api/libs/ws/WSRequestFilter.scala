/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import scala.concurrent.Future

/**
 * A request filter.  Override this trait to implement your own filters:
 *
 * {{{
 * class HeaderAppendingFilter(key: String, value: String) extends WSRequestFilter {
 *   override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
 *     WSRequestExecutor(r => executor(r.withHeaders((key, value))))
 *   }
 * }
 * }}}
 */
trait WSRequestFilter {
  def apply(next: WSRequestExecutor): WSRequestExecutor
}

trait WSRequestExecutor extends (StandaloneWSRequest => Future[StandaloneWSResponse])

object WSRequestExecutor {
  def apply(f: StandaloneWSRequest => Future[StandaloneWSResponse]): WSRequestExecutor = {
    new WSRequestExecutor {
      override def apply(v1: StandaloneWSRequest): Future[StandaloneWSResponse] = f.apply(v1)
    }
  }
}
