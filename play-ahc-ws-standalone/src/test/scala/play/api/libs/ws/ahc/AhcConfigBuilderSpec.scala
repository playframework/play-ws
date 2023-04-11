/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.ssl.Protocols
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import org.specs2.mutable.Specification
import play.api.libs.ws.WSClientConfig
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyServerSelector
import play.shaded.ahc.org.asynchttpclient.util.ProxyUtils

import scala.concurrent.duration._

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
      ahcConfig.getConnectTimeout must_== 120000
      ahcConfig.getRequestTimeout must_== 120000
      ahcConfig.getReadTimeout must_== 120000
    }

    "with basic options" should {

      "provide a basic default client with default settings" in {
        val config  = defaultConfig
        val builder = new AhcConfigBuilder(config)
        val actual  = builder.build()

        actual.getReadTimeout must_== defaultWsConfig.idleTimeout.toMillis
        actual.getRequestTimeout must_== defaultWsConfig.requestTimeout.toMillis
        actual.getConnectTimeout must_== defaultWsConfig.connectionTimeout.toMillis
        actual.isFollowRedirect must_== defaultWsConfig.followRedirects
        actual.getCookieStore must_== null

        actual.getEnabledProtocols.toSeq must not contain Protocols.deprecatedProtocols
      }

      "use an explicit idle timeout" in {
        val wsConfig = defaultWsConfig.copy(idleTimeout = 42.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getReadTimeout must_== 42L
      }

      "use an explicit request timeout" in {
        val wsConfig = defaultWsConfig.copy(requestTimeout = 47.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getRequestTimeout must_== 47L
      }

      "use an explicit connection timeout" in {
        val wsConfig = defaultWsConfig.copy(connectionTimeout = 99.millis)
        val config   = defaultConfig.copy(wsClientConfig = wsConfig)
        val builder  = new AhcConfigBuilder(config)

        val actual = builder.build()
        actual.getConnectTimeout must_== 99L
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
    }

    "with SSL options" should {

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

          sslConfig.protocol must_== "TLSv1.2"

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

        "should disable the hostname verifier if loose.acceptAnyCertificate is enabled" in {
          val underlyingConfig = parseSSLConfig("play.ws.ssl.loose.acceptAnyCertificate=true")
          val sslConfig        = SSLConfigFactory.parse(underlyingConfig)
          val wsConfig         = defaultWsConfig.copy(ssl = sslConfig)
          val config           = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder          = new AhcConfigBuilder(config)

          val asyncConfig = builder.build()
          asyncConfig.isUseInsecureTrustManager must beTrue
        }
      }

      "with protocols" should {

        "provide recommended protocols if not specified" in {
          val sslConfig         = SSLConfigSettings()
          val wsConfig          = defaultWsConfig.copy(ssl = sslConfig)
          val config            = defaultConfig.copy(wsClientConfig = wsConfig)
          val builder           = new AhcConfigBuilder(config)
          val existingProtocols = Array("TLSv1.2", "TLSv1.1", "TLSv1")

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
