/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import scala.concurrent.Future

/**
 * A request filter.  Override this trait to implement your own filters:
 *
 * {{{
 * import play.api.libs.ws.{ WSRequestFilter, WSRequestExecutor }
 *
 * class HeaderAppendingFilter(key: String, value: String) extends WSRequestFilter {
 *   override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
 *     WSRequestExecutor(r => executor(r.withHttpHeaders((key, value))))
 *   }
 * }
 * }}}
 */
trait WSRequestFilter extends (WSRequestExecutor => WSRequestExecutor)

object WSRequestFilter {

  /**
   * Creates an adhoc filter from a function:
   *
   * {{{
   * import play.api.libs.ws.{ WSRequestFilter, WSRequestExecutor }
   *
   * val filter: WSRequestFilter = WSRequestFilter { e =>
   *   WSRequestExecutor(r => e.apply(r.withQueryStringParameters("bed" -> "1")))
   * }
   * }}}
   *
   * @param f a function that returns executors
   * @return a filter that calls the passed in function.
   */
  def apply(f: WSRequestExecutor => WSRequestExecutor): WSRequestFilter = {
    new WSRequestFilter() {
      override def apply(v1: WSRequestExecutor): WSRequestExecutor = f(v1)
    }
  }
}

trait WSRequestExecutor extends (StandaloneWSRequest => Future[StandaloneWSResponse])

object WSRequestExecutor {
  def apply(f: StandaloneWSRequest => Future[StandaloneWSResponse]): WSRequestExecutor = {
    new WSRequestExecutor {
      override def apply(v1: StandaloneWSRequest): Future[StandaloneWSResponse] = f.apply(v1)
    }
  }
}
