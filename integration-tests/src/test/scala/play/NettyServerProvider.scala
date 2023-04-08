/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play

import akka.actor.ActorSystem
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.stream.Materializer

import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.core.server.NettyServer
import play.core.server.ServerConfig
import play.api.BuiltInComponents
import play.api.Mode

trait NettyServerProvider extends BeforeAfterAll {

  /**
   * @return Routes to be used by the test.
   */
  def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler]

  /**
   * The execution context environment.
   */
  def executionEnv: ExecutionEnv

  lazy val testServerPort: Int       = server.httpPort.getOrElse(sys.error("undefined port number"))
  val defaultTimeout: FiniteDuration = 5.seconds

  // Create Akka system for thread and streaming management
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

  override def beforeAll(): Unit = {}

  override def afterAll(): Unit = {
    server.stop()
    val terminate = system.terminate()
    Await.ready(terminate, defaultTimeout)
  }
}
