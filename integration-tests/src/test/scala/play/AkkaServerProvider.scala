/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import akka.stream.Materializer

trait AkkaServerProvider extends BeforeAfterAll {

  /**
   * @return Routes to be used by the test.
   */
  def routes: Route

  /**
   * The execution context environment.
   */
  def executionEnv: ExecutionEnv

  var testServerPort: Int            = _
  val defaultTimeout: FiniteDuration = 5.seconds

  // Create Akka system for thread and streaming management
  implicit val system       = ActorSystem()
  implicit val materializer = Materializer.matFromSystem

  lazy val futureServer: Future[Http.ServerBinding] = {
    // Using 0 (zero) means that a random free port will be used.
    // So our tests can run in parallel and won't mess with each other.
    Http().bindAndHandle(routes, "localhost", 0)
  }

  override def beforeAll(): Unit = {
    val portFuture = futureServer.map(_.localAddress.getPort)(executionEnv.executionContext)
    portFuture.foreach(port => testServerPort = port)(executionEnv.executionContext)
    Await.ready(portFuture, defaultTimeout)
  }

  override def afterAll(): Unit = {
    futureServer.foreach(_.unbind())(executionEnv.executionContext)
    val terminate = system.terminate()
    Await.ready(terminate, defaultTimeout)
  }
}
