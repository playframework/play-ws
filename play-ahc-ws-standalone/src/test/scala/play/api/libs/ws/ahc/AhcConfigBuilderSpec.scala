/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import javax.net.ssl.SSLContext

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.ssl.Protocols
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import org.specs2.mutable.Specification
import play.api.libs.ws.WSClientConfig
import play.shaded.ahc.io.netty.handler.ssl.SslContextBuilder
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyServerSelector
import play.shaded.ahc.org.asynchttpclient.util.ProxyUtils
import uk.org.lidalia.slf4jext.Level
import uk.org.lidalia.slf4jtest.TestLoggerFactory

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 */
class AhcConfigBuilderSpec extends Specification {

  val defaultWsConfig = WSClientConfig()
  val defaultConfig   = AhcWSClientConfig(defaultWsConfig)

  def parseSSLConfig(input: String): Config = {
    ConfigFactory.parseString(input).withFallback(ConfigFactory.defaultReference()).getConfig("play.ws.ssl")
  }

  "AhcConfigBuilder" should {
    "support overriding secure default values" in {
      val ahcConfig = new AhcConfigBuilder()
        .modifyUnderlying { builder =>
          builder.setCompressionEnforced(false)
          builder.setFollowRedirect(false)
        }
        .build()
      ahcConfig.isCompressionEnforced must beFalse
      ahcConfig.isFollowRedirect must beFalse
      ahcConfig.getConnectTimeout must_== java.time.Duration.ofMinutes(2)
      ahcConfig.getRequestTimeout must_== java.time.Duration.ofMinutes(2)
      ahcConfig.getReadTimeout must_== java.time.Duration.ofMinutes(2)
    }

    "with basic options" should {

      "provide a basic default client with default settings" in {
        val config  = defaultConfig
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()

        actual.getReadTimeout must_== java.time.Duration.ofMillis(defaultWsConfig.idleTimeout.toMillis)
        actual.getRequestTimeout must_== java.time.Duration.ofMillis(defaultWsConfig.requestTimeout.toMillis)
        actual.getConnectTimeout must_== java.time.Duration.ofMillis(defaultWsConfig.connectionTimeout.toMillis)
        actual.isFollowRedirect must_== defaultWsConfig.followRedirects
        actual.getCookieStore must_== null
        actual.isHttp2Enabled must beFalse
        actual.isRefuseSchemeDowngradeOnRedirect must beFalse
        actual.isRefuseCrossOriginBodyOnRedirect must beFalse

        actual.getEnabledProtocols.toSeq must not contain Protocols.deprecatedProtocols
      }

      "use an explicit idle timeout" in {
        val wsConfig = defaultWsConfig.copy(idleTimeout = 42.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getReadTimeout must_== java.time.Duration.ofMillis(42)
      }

      "use an explicit request timeout" in {
        val wsConfig = defaultWsConfig.copy(requestTimeout = 47.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getRequestTimeout must_== java.time.Duration.ofMillis(47)
      }

      "use an explicit connection timeout" in {
        val wsConfig = defaultWsConfig.copy(connectionTimeout = 99.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getConnectTimeout must_== java.time.Duration.ofMillis(99)
      }

      "use an explicit followRedirects option" in {
        val wsConfig = defaultWsConfig.copy(followRedirects = true)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.isFollowRedirect must_== true
      }

      "use an explicit proxy if useProxyProperties is true and there are system defined proxy settings" in {
        val wsConfig = defaultWsConfig.copy(useProxyProperties = true)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)

        // Used in ProxyUtils.createProxyServerSelector
        try {
          System.setProperty(ProxyUtils.PROXY_HOST, "localhost")
          val builder = new AhcConfigBuilder(config)
          val actual  = builder.build()

          val proxyServerSelector = actual.getProxyServerSelector

          proxyServerSelector must not(beNull)

          (proxyServerSelector must not).be_==(ProxyServerSelector.NO_PROXY_SELECTOR)
        } finally {
          // Unset http.proxyHost
          System.clearProperty(ProxyUtils.PROXY_HOST)
        }
      }
    }

    "with ahc options" should {

      "allow setting ahc keepAlive" in {
        val config  = defaultConfig.copy(keepAlive = false)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.isKeepAlive must_== false
      }

      "allow setting ahc maximumConnectionsPerHost" in {
        val config  = defaultConfig.copy(maxConnectionsPerHost = 3)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.getMaxConnectionsPerHost must_== 3
      }

      "allow setting ahc maximumConnectionsTotal" in {
        val config  = defaultConfig.copy(maxConnectionsTotal = 6)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.getMaxConnections must_== 6
      }

      "allow configuring HTTP/2" in {
        val config = defaultConfig.copy(
          http2Enabled = true,
          http2InitialWindowSize = Some(65535),
          http2MaxConcurrentStreams = Some(32)
        )
        val actual = new AhcConfigBuilder(config).build()

        actual.isHttp2Enabled must beTrue
        actual.getHttp2InitialWindowSize must_== 65535
        actual.getHttp2MaxConcurrentStreams must_== 32
      }

      "allow both redirect refusals to be enabled or disabled explicitly" in {
        val enabled = new AhcConfigBuilder(
          defaultConfig.copy(
            refuseSchemeDowngradeOnRedirect = Some(true),
            refuseCrossOriginBodyOnRedirect = Some(true)
          )
        ).build()
        val disabled = new AhcConfigBuilder(
          defaultConfig.copy(
            refuseSchemeDowngradeOnRedirect = Some(false),
            refuseCrossOriginBodyOnRedirect = Some(false)
          )
        ).build()

        (enabled.isRefuseSchemeDowngradeOnRedirect must beTrue)
          .and(
            enabled.isRefuseCrossOriginBodyOnRedirect must beTrue
          )
          .and(
            disabled.isRefuseSchemeDowngradeOnRedirect must beFalse
          )
          .and(
            disabled.isRefuseCrossOriginBodyOnRedirect must beFalse
          )
      }

      "let modifyUnderlying override redirect-refusal settings" in {
        val actual = new AhcConfigBuilder(
          defaultConfig.copy(
            refuseSchemeDowngradeOnRedirect = Some(true),
            refuseCrossOriginBodyOnRedirect = Some(true)
          )
        ).modifyUnderlying { builder =>
          builder.setRefuseSchemeDowngradeOnRedirect(false).setRefuseCrossOriginBodyOnRedirect(false)
        }.build()

        (actual.isRefuseSchemeDowngradeOnRedirect must beFalse).and(
          actual.isRefuseCrossOriginBodyOnRedirect must beFalse
        )
      }

      "preserve AHC defaults for unset optional settings" in {
        val actual = new AhcConfigBuilder(defaultConfig).build()

        actual.getHttp2InitialWindowSize must_== 16777216
        actual.getHttp2MaxConcurrentStreams must_== -1
        actual.getMaxDecompressedResponseSize must_== 268435456L
        actual.getHttp2MaxDecompressedResponseSize must_== 268435456L
      }

      "reject invalid programmatic HTTP/2 limits" in {
        (new AhcConfigBuilder(defaultConfig.copy(http2InitialWindowSize = Some(0))).build() must
          throwA[IllegalArgumentException]).and(
          new AhcConfigBuilder(defaultConfig.copy(http2MaxConcurrentStreams = Some(0))).build() must
            throwA[IllegalArgumentException]
        )
      }

      "allow limiting decompressed responses for both HTTP versions" in {
        val config = defaultConfig.copy(maxDecompressedResponseSize = Some(1024L))
        val actual = new AhcConfigBuilder(config).build()

        actual.getMaxDecompressedResponseSize must_== 1024L
        actual.getHttp2MaxDecompressedResponseSize must_== 1024L
      }

      "allow disabling decompressed response limits explicitly" in {
        val actual = new AhcConfigBuilder(defaultConfig.copy(maxDecompressedResponseSize = Some(0L))).build()

        actual.getMaxDecompressedResponseSize must_== 0L
        actual.getHttp2MaxDecompressedResponseSize must_== 0L
      }

      "reject a negative programmatic decompressed response limit" in {
        new AhcConfigBuilder(defaultConfig.copy(maxDecompressedResponseSize = Some(-1L))).build() must
          throwA[IllegalArgumentException]
      }

      "allow configuring event loop shutdown timing" in {
        val config = defaultConfig.copy(shutdownQuietPeriod = 25.millis, shutdownTimeout = 3.seconds)
        val actual = new AhcConfigBuilder(config).build()

        actual.getShutdownQuietPeriod must_== java.time.Duration.ofMillis(25)
        actual.getShutdownTimeout must_== java.time.Duration.ofSeconds(3)
      }

      "reject invalid programmatic event loop shutdown timing" in {
        (new AhcConfigBuilder(defaultConfig.copy(shutdownQuietPeriod = -1.millis)).build() must
          throwA[IllegalArgumentException])
          .and(
            new AhcConfigBuilder(defaultConfig.copy(shutdownTimeout = -1.millis)).build() must
              throwA[IllegalArgumentException]
          )
          .and(
            new AhcConfigBuilder(defaultConfig.copy(shutdownQuietPeriod = 1.second)).build() must
              throwA[IllegalArgumentException]
          )
      }

      "allow setting ahc maxNumberOfRedirects" in {
        val config  = defaultConfig.copy(maxNumberOfRedirects = 0)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.getMaxRedirects must_== 0
      }

      "allow setting ahc maxRequestRetry" in {
        val config  = defaultConfig.copy(maxRequestRetry = 99)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.getMaxRequestRetry must_== 99
      }

      "allow setting ahc disableUrlEncoding" in {
        val config  = defaultConfig.copy(disableUrlEncoding = true)
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()
        actual.isDisableUrlEncodingForBoundRequests must_== true
      }

      "allow setting ahc pool and cookie options" in {
        val config = defaultConfig.copy(
          maxConnectionLifetime = 3.minutes,
          idleConnectionInPoolTimeout = 4.seconds,
          connectionPoolCleanerPeriod = 5.seconds,
          useLaxCookieEncoder = true,
          useCookieStore = true
        )
        val actual = new AhcConfigBuilder(config).build()

        actual.getConnectionTtl must_== java.time.Duration.ofMinutes(3)
        actual.getPooledConnectionIdleTimeout must_== java.time.Duration.ofSeconds(4)
        actual.getConnectionPoolCleanerPeriod must_== java.time.Duration.ofSeconds(5)
        actual.isUseLaxCookieEncoder must beTrue
        actual.getCookieStore must not(beNull)
      }
    }

    "with SSL options" should {

      /** Creates an engine the way AHC does: it initializes the factory with the final configuration first. */
      def newSslEngine(asyncConfig: AsyncHttpClientConfig) = {
        val factory = asyncConfig.getSslEngineFactory
        factory.init(asyncConfig)
        factory.newSslEngine(asyncConfig, "localhost", 443)
      }

      // The ConfigSSLContextBuilder does most of the work here, but there are a couple of things outside of the
      // SSL context proper...

      "with context" should {

        "use the configured trustmanager and keymanager if context not passed in" in {
          // Stick a spy into the SSL config so we can verify that things get called on it that would
          // only be called if it was using the config trust manager...

          val sslConfig = SSLConfigSettings()
          val wsConfig  = defaultWsConfig.copy(ssl = sslConfig)
          val config    = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder   = new AhcConfigBuilder(config)

          sslConfig.protocol must_== "TLSv1.3"

          val asyncClientConfig = builder.build()

          // ...and return a result so specs2 is happy.
          asyncClientConfig.getSslEngineFactory must not(beNull)
        }

        "should validate certificates" in {
          val sslConfig = SSLConfigSettings()
          val wsConfig  = defaultWsConfig.copy(ssl = sslConfig)
          val config    = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder   = new AhcConfigBuilder(config)

          val asyncConfig = builder.build()
          asyncConfig.isUseInsecureTrustManager must beFalse
        }

        "should keep SSL debug tracing and hostname verification" in {
          val underlyingConfig = parseSSLConfig("play.ws.ssl.debug.keymanager=true")
          val sslConfig        = SSLConfigFactory.parse(underlyingConfig)
          val wsConfig         = defaultWsConfig.copy(ssl = sslConfig)
          val config           = defaultConfig.copy(wsClientConfig = wsConfig)
          val asyncConfig      = new AhcConfigBuilder(config).build()
          val sslEngine        = newSslEngine(asyncConfig)

          sslEngine.getClass.getName must contain("TracingSSLEngine")
          sslEngine.getSSLParameters.getEndpointIdentificationAlgorithm must_== "HTTPS"
        }

        "should disable the hostname verifier if loose.acceptAnyCertificate is enabled" in {
          val underlyingConfig = parseSSLConfig("play.ws.ssl.loose.acceptAnyCertificate=true")
          val sslConfig        = SSLConfigFactory.parse(underlyingConfig)
          val wsConfig         = defaultWsConfig.copy(ssl = sslConfig)
          val config           = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder          = new AhcConfigBuilder(config)

          val asyncConfig = builder.build()
          asyncConfig.isUseInsecureTrustManager must beTrue
          newSslEngine(asyncConfig).getSSLParameters.getEndpointIdentificationAlgorithm must beNull
        }

        "should verify the hostname with every TLS context" in {
          val default = SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.default=true"))
          Seq(SSLConfigSettings(), default)
            .map { sslConfig =>
              val config      = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig))
              val asyncConfig = new AhcConfigBuilder(config).build()
              newSslEngine(asyncConfig).getSSLParameters.getEndpointIdentificationAlgorithm must_== "HTTPS"
            }
            .reduce(_ and _)
        }

        "should not verify the hostname if AHC's endpoint identification is disabled" in {
          val asyncConfig = new AhcConfigBuilder(defaultConfig)
            .modifyUnderlying(_.setDisableHttpsEndpointIdentificationAlgorithm(true))
            .build()
          newSslEngine(asyncConfig).getSSLParameters.getEndpointIdentificationAlgorithm must beNull
        }
      }

      "with engines" should {

        "apply the configured protocols and cipher suites" in {
          val underlyingConfig = parseSSLConfig(
            """play.ws.ssl.enabledProtocols=["TLSv1.2"]
              |play.ws.ssl.enabledCipherSuites=["TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"]""".stripMargin
          )
          val sslConfig = SSLConfigFactory.parse(underlyingConfig)
          val loose     = sslConfig.withLoose(sslConfig.loose.withAcceptAnyCertificate(true))
          Seq(sslConfig, loose)
            .map { ssl =>
              val config    = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = ssl))
              val sslEngine = newSslEngine(new AhcConfigBuilder(config).build())
              (sslEngine.getEnabledProtocols.toSeq must_== Seq("TLSv1.2"))
                .and(sslEngine.getEnabledCipherSuites.toSeq must_== Seq("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"))
            }
            .reduce(_ and _)
        }

        "apply protocols and cipher suites changed through modifyUnderlying" in {
          val asyncConfig = new AhcConfigBuilder(defaultConfig)
            .modifyUnderlying(
              _.setEnabledProtocols(Array("TLSv1.2"))
                .setEnabledCipherSuites(Array("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
            )
            .build()
          val sslEngine = newSslEngine(asyncConfig)

          (sslEngine.getEnabledProtocols.toSeq must_== Seq("TLSv1.2"))
            .and(sslEngine.getEnabledCipherSuites.toSeq must_== Seq("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
        }

        "offer HTTP/2 through ALPN only when it is enabled and allowed for the connection" in {
          val sslConfigs = Seq(
            SSLConfigSettings(),
            SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.loose.acceptAnyCertificate=true")),
            SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.default=true")),
            SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.debug.ssl=true"))
          )
          val results = for {
            sslConfig    <- sslConfigs
            http2Enabled <- Seq(false, true)
          } yield {
            val config =
              defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig), http2Enabled = http2Enabled)
            val asyncConfig = new AhcConfigBuilder(config).build()
            val factory     = asyncConfig.getSslEngineFactory
            factory.init(asyncConfig)
            def offered(http2Allowed: Boolean) =
              factory
                .newSslEngine(asyncConfig, "localhost", 443, http2Allowed)
                .getSSLParameters
                .getApplicationProtocols
                .toSeq
            if (http2Enabled) (offered(true) must_== Seq("h2", "http/1.1")).and(offered(false) must_== Seq("http/1.1"))
            else (offered(true) must beEmpty).and(offered(false) must beEmpty)
          }
          results.reduce(_ and _)
        }

        "keep hostname verification when a debug-traced engine offers HTTP/2" in {
          val sslConfig = SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.debug.ssl=true"))
          val config = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig), http2Enabled = true)
          val asyncConfig = new AhcConfigBuilder(config).build()
          val parameters  = newSslEngine(asyncConfig).getSSLParameters

          (parameters.getEndpointIdentificationAlgorithm must_== "HTTPS")
            .and(parameters.getApplicationProtocols.toSeq must_== Seq("h2", "http/1.1"))
        }

        "use an SslContext set through modifyUnderlying only on the loose path" in {
          val userContext = SslContextBuilder.forClient().protocols("TLSv1.2").build()
          val loose       = SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.loose.acceptAnyCertificate=true"))
          def engine(sslConfig: SSLConfigSettings) =
            newSslEngine(
              new AhcConfigBuilder(defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig)))
                .modifyUnderlying(_.setSslContext(userContext))
                .build()
            )
          val normal = engine(SSLConfigSettings())

          (engine(loose).getEnabledProtocols.toSeq must_== Seq("TLSv1.2"))
            .and(normal.getEnabledProtocols.toSeq must contain("TLSv1.3"))
            .and(normal.getSSLParameters.getEndpointIdentificationAlgorithm must_== "HTTPS")
        }

        "warn when an SslContext set through modifyUnderlying is ignored" in {
          val logger      = new TestLoggerFactory(Level.OFF).getLogger("ignored-ssl-context")
          val asyncConfig = new AhcConfigBuilder(defaultConfig)
            .modifyUnderlying(_.setSslContext(SslContextBuilder.forClient().build()))
            .build()
          new ConfiguredSslEngineFactory(SSLContext.getDefault, loose = false, logger).init(asyncConfig)
          new ConfiguredSslEngineFactory(SSLContext.getDefault, loose = true, logger).init(asyncConfig)
          new ConfiguredSslEngineFactory(SSLContext.getDefault, loose = false, logger).init(
            new AhcConfigBuilder(defaultConfig).build()
          )

          logger.getLoggingEvents.asScala.map(_.getLevel) must_== Seq(Level.WARN)
        }

        "let an SslContext used on the loose path decide ALPN itself" in {
          val loose       = SSLConfigFactory.parse(parseSSLConfig("play.ws.ssl.loose.acceptAnyCertificate=true"))
          val config      = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = loose), http2Enabled = true)
          val asyncConfig = new AhcConfigBuilder(config)
            .modifyUnderlying(_.setSslContext(SslContextBuilder.forClient().build()))
            .build()

          newSslEngine(asyncConfig).getSSLParameters.getApplicationProtocols must beEmpty
        }

        "fail to build when no configured protocol is enabled in the SSL context" in {
          val sslConfig = SSLConfigFactory.parse(parseSSLConfig("""play.ws.ssl.enabledProtocols=["TLSv0"]"""))
          val config    = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig))

          new AhcConfigBuilder(config).build() must throwAn[IllegalArgumentException](
            "None of the TLS protocols \\[TLSv0\\] is enabled in the SSL context.*"
          )
        }

        "fail to build when no configured cipher suite is enabled in the SSL context" in {
          val sslConfig =
            SSLConfigFactory.parse(parseSSLConfig("""play.ws.ssl.enabledCipherSuites=["NO_SUCH_SUITE"]"""))
          val config = defaultConfig.copy(wsClientConfig = defaultWsConfig.copy(ssl = sslConfig))

          new AhcConfigBuilder(config).build() must throwAn[IllegalArgumentException](
            "None of the cipher suites \\[NO_SUCH_SUITE\\] is enabled in the SSL context.*"
          )
        }
      }

      "with protocols" should {

        "provide recommended protocols if not specified" in {
          val sslConfig         = SSLConfigSettings()
          val wsConfig          = defaultWsConfig.copy(ssl = sslConfig)
          val config            = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder           = new AhcConfigBuilder(config)
          val existingProtocols = Array("TLSv1.3", "TLSv1.2")

          val actual = builder.configureProtocols(existingProtocols, sslConfig)

          actual.toSeq must containTheSameElementsAs(Protocols.recommendedProtocols.toIndexedSeq)
        }

        "provide explicit protocols if specified" in {
          val underlyingConfig  = parseSSLConfig("""play.ws.ssl.enabledProtocols=["derp", "baz", "quux"]""")
          val sslConfig         = SSLConfigFactory.parse(underlyingConfig)
          val wsConfig          = defaultWsConfig.copy(ssl = sslConfig)
          val config            = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder           = new AhcConfigBuilder(config)
          val existingProtocols = Array("quux", "derp", "baz")

          val actual = builder.configureProtocols(existingProtocols, sslConfig)

          actual.toSeq must containTheSameElementsAs(Seq("derp", "baz", "quux"))
        }
      }

      "with ciphers" should {

        "provide explicit ciphers if specified" in {
          val underlyingConfig = parseSSLConfig("""play.ws.ssl.enabledCipherSuites=["goodone", "goodtwo"]""")
          val sslConfig        = SSLConfigFactory.parse(underlyingConfig)
          val wsConfig         = defaultWsConfig.copy(ssl = sslConfig)
          val config           = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder          = new AhcConfigBuilder(config)
          val existingCiphers  = Array("goodone", "goodtwo", "goodthree")

          val actual = builder.configureCipherSuites(existingCiphers, sslConfig)

          actual.toSeq must containTheSameElementsAs(Seq("goodone", "goodtwo"))
        }
      }
    }
  }
}
