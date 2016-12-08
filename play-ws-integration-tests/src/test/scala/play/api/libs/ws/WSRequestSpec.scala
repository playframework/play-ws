/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.mutable.{After, Specification}
import org.specs2.specification.Scope
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WSRequestSpec extends Specification {

  sequential

  abstract class WithWSClient extends Scope with After {
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val ws = AhcWSClient()

    override def after = {
      ws.close()
      Await.result(actorSystem.terminate(), Duration.Inf)
    }
  }

  "WSRequestHolder" should {

    "give the full URL with the query string" in new WithWSClient {
      ws.url("http://foo.com").uri.toString must equalTo("http://foo.com")

      ws.url("http://foo.com").withQueryString("bar" -> "baz").uri.toString must equalTo("http://foo.com?bar=baz")

      ws.url("http://foo.com").withQueryString("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo("http://foo.com?bar=bah&bar=baz")

    }

    "correctly URL-encode the query string part" in new WithWSClient {
      ws.url("http://foo.com").withQueryString("&" -> "=").uri.toString must equalTo("http://foo.com?%26=%3D")

    }

  }

}
