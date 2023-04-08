/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import org.apache.pekko.stream.Materializer

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Millis
import org.scalatest.time.Span
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.core.server.NettyServer
import play.core.server.ServerConfig
import play.api.BuiltInComponents
import play.api.Mode

trait NettyServerProvider extends BeforeAndAfterAll with ScalaFutures { self: Suite =>

  final implicit override def patienceConfig: PatienceConfig =
    PatienceConfig(Span(3000, Millis))

  /**
   * @return Routes to be used by the test.
   */
  def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler]

  protected implicit def executionContext: ExecutionContext = ExecutionContext.global

  lazy val testServerPort: Int       = server.httpPort.getOrElse(sys.error("undefined port number"))
  val defaultTimeout: FiniteDuration = 5.seconds

  // Create Pekko system for thread and streaming management
  implicit val system: ActorSystem        = ActorSystem()
  implicit val materializer: Materializer = Materializer.matFromSystem

  // Using 0 (zero) means that a random free port will be used.
  // So our tests can run in parallel and won't mess with each other.
  val server = NettyServer.fromRouterWithComponents(
    ServerConfig(
      port = Option(0),
      mode = Mode.Test
    )
  )(components => routes(components))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    server.stop()
    val terminate = system.terminate()
    Await.ready(terminate, defaultTimeout)
  }
}
