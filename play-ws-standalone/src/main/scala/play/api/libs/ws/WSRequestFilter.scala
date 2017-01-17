/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import scala.concurrent.Future

trait WSRequestExecutor[-Request, +Response] {
  def execute(request: Request): Future[Response]
}

trait WSRequestFilter[Request <: StandaloneWSRequest, Response <: StandaloneWSResponse] {
  def apply(next: WSRequestExecutor[Request, Response]): WSRequestExecutor[Request, Response]
}
