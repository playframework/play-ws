/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play

import org.specs2.execute.Result
import org.specs2.mutable.Specification

trait PendingSupport { self: Specification =>

  trait Backend {
    val matching: Boolean
  }

  case class Ahc private (matching: Boolean) extends Backend
  object Ahc {
    def apply(c: play.libs.ws.StandaloneWSClient): Ahc =
      Ahc(c.isInstanceOf[play.libs.ws.ahc.StandaloneAhcWSClient])
    def apply(c: play.api.libs.ws.StandaloneWSClient): Ahc =
      Ahc(c.isInstanceOf[play.api.libs.ws.ahc.StandaloneAhcWSClient])
  }

  case class AkkaHttp private (matching: Boolean) extends Backend
  object AkkaHttp {
    def apply(c: play.libs.ws.StandaloneWSClient): AkkaHttp =
      AkkaHttp(c.isInstanceOf[play.libs.ws.akkahttp.StandaloneAkkaHttpWSClient])
    def apply(c: play.api.libs.ws.StandaloneWSClient): AkkaHttp =
      AkkaHttp(c.isInstanceOf[play.api.libs.ws.akkahttp.StandaloneAkkaHttpWSClient])
  }

  def pendingFor(backend: Backend, reason: String)(test: => Result): Result = backend match {
    case Ahc(true) => pending(s"!!! PENDING !!! AHC backend $reason")
    case AkkaHttp(true) => pending(s"!!! PENDING !!! Akka Http backend $reason")
    case _ => test
  }

}
