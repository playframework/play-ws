package play.api.libs.ws.ahc

import org.specs2.execute._

trait AkkaHttpPending {

  implicit def toAkkaHttpPending[T: AsResult](t: => T): AkkaHttpPending[T] =
    new AkkaHttpPending(t)

  class AkkaHttpPending[T: AsResult](t: => T) {
    /** @return Pending Akka Http test */
    def akkaHttpPending(m: String): Result = {
      val result = ResultExecution.execute(AsResult(t))
      result match {
        case s @ Success(_, _) => Failure(s"Fixed now, you should remove the 'akkaHttpPending' marker: $m")
        case other => Pending(s"[Akka Http Pending: $m]")
      }
    }
  }
}
