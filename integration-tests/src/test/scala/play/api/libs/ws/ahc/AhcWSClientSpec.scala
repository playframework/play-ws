/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

class AhcWSClientSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers {

  sequential

  val testServerPort = 49231

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client
  val client = StandaloneAhcWSClient()

  override def afterAll = {
    client.close()
    system.terminate()
  }

  "url" should {
    "throw an exception on invalid url" in {
      { client.url("localhost") } must throwAn[IllegalArgumentException]
    }

    "not throw exception on valid url" in {
      { client.url("http://localhost:9000") } must not(throwAn[IllegalArgumentException])
    }
  }
}
