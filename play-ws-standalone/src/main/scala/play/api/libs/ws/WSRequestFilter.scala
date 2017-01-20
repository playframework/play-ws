/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import scala.concurrent.Future

trait WSRequestExecutor {
  def execute(request: StandaloneWSRequest): Future[StandaloneWSResponse]
}

trait WSRequestFilter {
  def apply(next: WSRequestExecutor): WSRequestExecutor
}
