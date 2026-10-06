/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.apache.pekko.stream.scaladsl.Sink
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.DefaultBodyReadables
import play.api.libs.ws.DefaultWSProxyServer
import play.api.libs.ws.WSAuthScheme
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class HttpsHttp2Spec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with DefaultBodyReadables {

  sequential

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = { case _ =>
    components.defaultActionBuilder(Results.NotFound)
  }

  private def http2Config(server: AlpnProtocolTestServer, settings: String*): AhcWSClientConfig =
    AhcWSClientConfigFactory.forConfig(
      ConfigFactory
        .parseString(
          (s"""play.ws.ssl.trustManager.stores = [{ type = "PKCS12", path = "${server
              .trustStorePath()}", password = "changeit" }]""" +: "play.ws.ahc.http2Enabled = true" +: settings)
            .mkString("\n")
        )
        .withFallback(ConfigFactory.defaultReference())
    )

  private def offered(server: AlpnProtocolTestServer): Seq[String] =
    Option(server.lastHandshake().offeredProtocols()).map(_.asScala.toSeq).getOrElse(Nil)

  private def withServer(server: AlpnProtocolTestServer)(block: AlpnProtocolTestServer => Result): Result =
    try block(server)
    finally server.close()

  "Play WS with HTTP/2 enabled" should {

    "fall back to HTTP/1.1 over HTTPS when the server does not select h2" in withServer(
      new AlpnProtocolTestServer(null, false, false)
    ) { server =>
      withClient(http2Config(server)) { client =>
        (Await.result(client.url(server.url()).get(), 5.seconds).body[String] must_== "http/1.1")
          .and(offered(server) must_== Seq("h2", "http/1.1"))
      }
    }

    "offer only http/1.1 for a request authenticated per connection" in withServer(new AlpnProtocolTestServer()) {
      server =>
        withClient(http2Config(server)) { client =>
          val response =
            Await.result(client.url(server.url()).withAuth("user", "password", WSAuthScheme.NTLM).get(), 5.seconds)
          (response.body[String] must_== "http/1.1").and(offered(server) must_== Seq("http/1.1"))
        }
    }

    "keep the configured client certificate and TLS protocols over h2" in withServer(
      new AlpnProtocolTestServer(null, true, true)
    ) { server =>
      val config = http2Config(
        server,
        s"""play.ws.ssl.keyManager.stores = [{ type = "PKCS12", path = "${server
            .clientKeyStorePath()}", password = "changeit" }]""",
        "play.ws.ssl.enabledProtocols = [TLSv1.2]"
      )
      withClient(config) { client =>
        (Await.result(client.url(server.url()).get(), 5.seconds).body[String] must_== "h2")
          .and(server.lastHandshake().tlsVersion() must_== "TLSv1.2")
          .and(server.lastHandshake().clientCertificate() must_== "CN=play-ws-test-client")
      }
    }

    "offer h2 through ALPN with every SSL debug option, on the normal and the loose path" in withServer(
      new AlpnProtocolTestServer()
    ) { server =>
      // The loose path never used ssl-config's tracing context, so there this only shows that the debug options do
      // not break ALPN, not that they produce trace output.
      val results = for {
        option <- Seq("all", "keymanager", "ssl", "sslctx", "trustmanager")
        loose  <- Seq(false, true)
      } yield {
        val config = http2Config(
          server,
          s"play.ws.ssl.debug.$option = true",
          s"play.ws.ssl.loose.acceptAnyCertificate = $loose"
        )
        withClient(config) { client =>
          (Await.result(client.url(server.url()).get(), 5.seconds).body[String] must_== "h2")
            .setMessage(s"debug=$option, loose=$loose")
        }
      }
      results.reduce(_ and _)
    }

    "open an HTTP/1.1 connection for a request authenticated per connection after using h2" in withServer(
      new AlpnProtocolTestServer()
    ) { server =>
      withClient(http2Config(server)) { client =>
        val first  = Await.result(client.url(server.url()).get(), 5.seconds).body[String]
        val second =
          Await.result(client.url(server.url()).withAuth("user", "password", WSAuthScheme.NTLM).get(), 5.seconds)
        (first must_== "h2").and(second.body[String] must_== "http/1.1").and(offered(server) must_== Seq("http/1.1"))
      }
    }

    "negotiate with the origin through an HTTP CONNECT tunnel" in withServer(new AlpnProtocolTestServer()) { server =>
      val proxy = new ConnectProxyTestServer()
      try {
        val results = Seq(false, true).map { http2Enabled =>
          withClient(http2Config(server, s"play.ws.ahc.http2Enabled = $http2Enabled")) { client =>
            val proxied = client.url(server.url()).withProxyServer(DefaultWSProxyServer("127.0.0.1", proxy.port()))
            val body    = Await.result(proxied.get(), 5.seconds).body[String]
            if (http2Enabled) (body must_== "h2").and(offered(server) must_== Seq("h2", "http/1.1"))
            else (body must_== "http/1.1").and(server.lastHandshake().offeredProtocols() must beNull)
          }
        }
        val origin = server.url().stripPrefix("https://").stripSuffix("/")
        results.reduce(_ and _).and(proxy.connectTargets().asScala.toSeq must_== Seq(origin, origin))
      } finally {
        proxy.close()
      }
    }

    "keep a demanded h2 stream moving over HTTPS while a sibling is suspended" in {
      val server = new Http2StreamingTestServer(true)
      // A stream window above the connection window (65,535 bytes) lets the suspended stream alone use up the
      // connection's credit. AHC returns connection credit while a response is suspended; its own tests verify that.
      val config = AhcWSClientConfigFactory.forConfig(
        ConfigFactory
          .parseString(
            s"""play.ws.ssl.trustManager.stores = [{ type = "PKCS12", path = "${server
                .trustStorePath()}", password = "changeit" }]
               |play.ws.ahc.http2Enabled = true
               |play.ws.ahc.http2InitialWindowSize = 131072
               |play.ws.ahc.maxConnectionsPerHost = 1""".stripMargin
          )
          .withFallback(ConfigFactory.defaultReference())
      )
      try {
        withClient(config) { client =>
          val suspended = Await.result(client.url(server.url("/suspended")).stream(), 5.seconds)
          val queued    = server.awaitSuspendedResponseQueued(5, TimeUnit.SECONDS)
          val sibling   = Await.result(client.url(server.url("/sibling")).stream(), 5.seconds)
          val received  =
            Await.result(sibling.bodyAsSource.runFold(0L)((total, chunk) => total + chunk.length), 10.seconds)
          // Checked before cancelling, which may release the suspended stream's credit.
          val heldBack = server.suspendedResponseStillPending()
          suspended.bodyAsSource.runWith(Sink.cancelled)

          (queued must beTrue).toResult
            .and((received must_== server.siblingResponseBytes()).toResult)
            .and((server.connectionCount() must_== 1).toResult)
            .and((heldBack must beTrue.setMessage("the suspended response was not held back")).toResult)
        }
      } finally {
        server.close()
      }
    }
  }
}
