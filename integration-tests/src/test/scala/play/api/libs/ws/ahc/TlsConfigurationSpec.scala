/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.NettyServerProvider
import play.api.BuiltInComponents
import play.api.libs.ws.DefaultBodyReadables
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.shaded.ahc.io.netty.handler.ssl.SslContextBuilder
import play.shaded.ahc.io.netty.handler.ssl.util.InsecureTrustManagerFactory
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class TlsConfigurationSpec(implicit val executionEnv: ExecutionEnv)
    extends Specification
    with NettyServerProvider
    with StandaloneWSClientSupport
    with DefaultBodyReadables {

  sequential

  override def routes(components: BuiltInComponents): PartialFunction[RequestHeader, Handler] = { case _ =>
    components.defaultActionBuilder(Results.NotFound)
  }

  private def clientConfig(settings: String*): AhcWSClientConfig =
    AhcWSClientConfigFactory.forConfig(
      ConfigFactory.parseString(settings.mkString("\n")).withFallback(ConfigFactory.defaultReference())
    )

  private def trusting(server: AlpnProtocolTestServer) =
    s"""play.ws.ssl.trustManager.stores = [{ type = "PKCS12", path = "${server
        .trustStorePath()}", password = "changeit" }]"""

  private def clientKey(server: AlpnProtocolTestServer) =
    s"""play.ws.ssl.keyManager.stores = [{ type = "PKCS12", path = "${server
        .clientKeyStorePath()}", password = "changeit" }]"""

  private val loose = "play.ws.ssl.loose.acceptAnyCertificate = true"

  private def get(client: StandaloneAhcWSClient, url: String): String =
    Await.result(client.url(url).get(), 5.seconds).body[String]

  private def failureMessages(result: Try[?]): List[String] =
    result.failed.toOption.toList.flatMap(Iterator.iterate(_)(_.getCause).takeWhile(_ != null)).flatMap { cause =>
      Option(cause.getMessage)
    }

  private def withServer(server: AlpnProtocolTestServer)(block: AlpnProtocolTestServer => Result): Result =
    try block(server)
    finally server.close()

  "Play WS TLS configuration" should {

    "negotiate only the configured TLS protocols" in withServer(new AlpnProtocolTestServer()) { server =>
      Seq(trusting(server), loose)
        .map { trust =>
          withClient(clientConfig(trust, "play.ws.ssl.enabledProtocols = [TLSv1.2]")) { client =>
            (get(client, server.url()) must_== "http/1.1")
              .and(server.lastHandshake().tlsVersion() must_== "TLSv1.2")
          }
        }
        .reduce(_ and _)
    }

    "negotiate only the configured cipher suites" in withServer(new AlpnProtocolTestServer()) { server =>
      Seq(trusting(server), loose)
        .map { trust =>
          withClient(clientConfig(trust, "play.ws.ssl.enabledCipherSuites = [TLS_AES_128_GCM_SHA256]")) { client =>
            (get(client, server.url()) must_== "http/1.1")
              .and(server.lastHandshake().cipherSuite() must_== "TLS_AES_128_GCM_SHA256")
          }
        }
        .reduce(_ and _)
    }

    "apply TLS protocols changed through modifyUnderlying" in withServer(new AlpnProtocolTestServer()) { server =>
      val ahcConfig = new AhcConfigBuilder(clientConfig(trusting(server)))
        .modifyUnderlying(_.setEnabledProtocols(Array("TLSv1.2")))
        .build()
      val client = new StandaloneAhcWSClient(new DefaultAsyncHttpClient(ahcConfig))
      try {
        (get(client, server.url()) must_== "http/1.1")
          .and(server.lastHandshake().tlsVersion() must_== "TLSv1.2")
      } finally {
        client.close()
      }
    }

    "use an SslContext set through modifyUnderlying on the loose path only" in withServer(
      new AlpnProtocolTestServer()
    ) { server =>
      val userContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).protocols("TLSv1.2").build()
      def tlsVersionWith(trust: String): String = {
        val ahcConfig = new AhcConfigBuilder(clientConfig(trust)).modifyUnderlying(_.setSslContext(userContext)).build()
        val client    = new StandaloneAhcWSClient(new DefaultAsyncHttpClient(ahcConfig))
        try {
          get(client, server.url())
          server.lastHandshake().tlsVersion()
        } finally {
          client.close()
        }
      }

      (tlsVersionWith(loose) must_== "TLSv1.2").and(tlsVersionWith(trusting(server)) must_== "TLSv1.3")
    }

    "present the configured client certificate, also on the loose path" in withServer(
      new AlpnProtocolTestServer(null, true, true)
    ) { server =>
      Seq(trusting(server), loose)
        .map { trust =>
          withClient(clientConfig(trust, clientKey(server))) { client =>
            (get(client, server.url()) must_== "http/1.1")
              .and(server.lastHandshake().clientCertificate() must_== "CN=play-ws-test-client")
          }
        }
        .reduce(_ and _)
    }

    "reject a certificate for another host unless the loose path accepts any certificate" in withServer(
      new AlpnProtocolTestServer()
    ) { server =>
      val otherHost = server.url().replace("localhost", "127.0.0.1")
      val rejected  = withClient(clientConfig(trusting(server))) { client =>
        val result = Try(get(client, otherHost))
        (result must beFailedTry).toResult
          .and((failureMessages(result).exists(_.contains("subject alternative")) must beTrue).toResult)
      }
      val accepted = withClient(clientConfig(loose)) { client =>
        get(client, otherHost) must_== "http/1.1"
      }
      rejected.and(accepted)
    }
  }
}
