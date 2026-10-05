/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.DefaultBodyReadables
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class SslDebugHostnameSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with DefaultBodyReadables {

  sequential

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = { case _ =>
    components.defaultActionBuilder(Results.NotFound)
  }

  private val debugOptions =
    Seq(None, Some("all"), Some("keymanager"), Some("ssl"), Some("sslctx"), Some("trustmanager"))

  private def clientConfig(server: WrongHostTlsTestServer, debugOption: Option[String], withClientKey: Boolean) = {
    val debugSetting = debugOption.map(name => s"play.ws.ssl.debug.$name = true").getOrElse("")
    val keySetting   =
      if (withClientKey)
        s"""play.ws.ssl.keyManager.stores = [{ type = "PKCS12", path = "${server
            .clientKeyStorePath()}", password = "changeit" }]"""
      else ""
    val config = ConfigFactory
      .parseString(
        s"""play.ws.ssl.trustManager.stores = [{ type = "PKCS12", path = "${server
            .trustStorePath()}", password = "changeit" }]
           |$keySetting
           |$debugSetting""".stripMargin
      )
      .withFallback(ConfigFactory.defaultReference())
    AhcWSClientConfigFactory.forConfig(config)
  }

  /** Fails unless the request failed because the certificate did not match the requested host. */
  private def rejectedForHostname(debugOption: Option[String], result: Try[?]) = {
    val causes = Iterator.iterate(result.failed.toOption.orNull)(_.getCause).takeWhile(_ != null).toList
    val reason = causes.map(cause => s"${cause.getClass.getName}: ${cause.getMessage}").mkString(" <- ")
    (result.isFailure must beTrue.setMessage(s"debug=$debugOption: wrong-host request was accepted"))
      .and(
        causes.exists(cause => Option(cause.getMessage).exists(_.contains("subject alternative"))) must beTrue
          .setMessage(s"debug=$debugOption: not rejected for the hostname: $reason")
      )
  }

  "SSL debug options" should {
    "retain hostname verification for a trusted certificate" in {
      val server = new WrongHostTlsTestServer()
      try {
        debugOptions
          .map { option =>
            withClient(clientConfig(server, option, withClientKey = false)) { client =>
              val accepted = Await.result(client.url(server.matchingUrl()).get(), 5.seconds)
              val rejected = Try(Await.result(client.url(server.wrongHostUrl()).get(), 5.seconds))
              (accepted.status must_== 200).and(rejectedForHostname(option, rejected))
            }
          }
          .reduce(_ and _)
      } finally {
        server.close()
      }
    }

    "keep presenting the configured client certificate and verifying the hostname" in {
      val server = new WrongHostTlsTestServer(true)
      try {
        debugOptions
          .map { option =>
            withClient(clientConfig(server, option, withClientKey = true)) { client =>
              val accepted = Await.result(client.url(server.matchingUrl()).get(), 5.seconds)
              val rejected = Try(Await.result(client.url(server.wrongHostUrl()).get(), 5.seconds))
              (accepted.status must_== 200)
                .and(accepted.body[String] must_== "client=CN=play-ws-test-client")
                .and(rejectedForHostname(option, rejected))
            }
          }
          .reduce(_ and _)
      } finally {
        server.close()
      }
    }
  }
}
