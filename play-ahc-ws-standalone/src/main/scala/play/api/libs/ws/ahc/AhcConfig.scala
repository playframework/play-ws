/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.time.{ Duration => JDuration }

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import javax.net.ssl._

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.ssl._
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.WSConfigParser
import play.shaded.ahc.io.netty.handler.ssl.util.InsecureTrustManagerFactory
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig

import scala.concurrent.duration._

/**
 * Ahc client config.
 *
 * @param wsClientConfig The general WS client config.
 * @param maxConnectionsPerHost The maximum number of connections to make per host. -1 means no maximum.
 * @param maxConnectionsTotal The maximum total number of connections. -1 means no maximum.
 * @param maxConnectionLifetime The maximum time that a connection should live for in the pool.
 * @param idleConnectionInPoolTimeout The time after which a connection that has been idle in the pool should be closed.
 * @param connectionPoolCleanerPeriod the frequency to cleanup timeout idle connections
 * @param maxNumberOfRedirects The maximum number of redirects.
 * @param maxRequestRetry The maximum number of times to retry a request if it fails.
 * @param disableUrlEncoding Whether the raw URL should be used.
 * @param keepAlive keeps thread pool active, replaces allowPoolingConnection and allowSslConnectionPool
 * @param useLaxCookieEncoder whether to use LAX(no cookie name/value verification) or STRICT (verifies cookie name/value) cookie decoder
 * @param http2Enabled Whether HTTP/2 is allowed by AHC. Play WS does not yet offer HTTP/2 over HTTPS;
 *                     cleartext HTTP/2 also requires enabling AHC's http2CleartextEnabled setting.
 * @param http2InitialWindowSize The HTTP/2 initial per-stream flow-control window in bytes. None uses the AHC default.
 * @param http2MaxConcurrentStreams The maximum number of concurrent HTTP/2 streams per connection. None uses the AHC default.
 * @param maxDecompressedResponseSize The maximum decompressed size of one response in bytes. None uses the AHC defaults.
 * @param shutdownQuietPeriod The quiet period used when shutting down AHC's event loop.
 * @param shutdownTimeout The maximum time allowed when shutting down AHC's event loop.
 * @param refuseSchemeDowngradeOnRedirect Whether to refuse an HTTPS-to-HTTP redirect. None uses the AHC default.
 * @param refuseCrossOriginBodyOnRedirect Whether to refuse a redirect that would replay a body across origins.
 *                                        None uses the AHC default.
 * @note For compatibility with Play WS 3.0, positional pattern matching exposes only the original 12 fields even
 *       though this case class's product contains all 20 fields. Access the additional settings by name.
 */
case class AhcWSClientConfig(
    wsClientConfig: WSClientConfig = WSClientConfig(),
    maxConnectionsPerHost: Int = -1,
    maxConnectionsTotal: Int = -1,
    maxConnectionLifetime: Duration = Duration.Inf,
    idleConnectionInPoolTimeout: Duration = 1.minute,
    connectionPoolCleanerPeriod: Duration = 1.second,
    maxNumberOfRedirects: Int = 5,
    maxRequestRetry: Int = 5,
    disableUrlEncoding: Boolean = false,
    keepAlive: Boolean = true,
    useLaxCookieEncoder: Boolean = false,
    useCookieStore: Boolean = false,
    http2Enabled: Boolean = false,
    http2InitialWindowSize: Option[Int] = None,
    http2MaxConcurrentStreams: Option[Int] = None,
    maxDecompressedResponseSize: Option[Long] = None,
    shutdownQuietPeriod: FiniteDuration = Duration.Zero,
    shutdownTimeout: FiniteDuration = Duration.Zero,
    refuseSchemeDowngradeOnRedirect: Option[Boolean] = None,
    refuseCrossOriginBodyOnRedirect: Option[Boolean] = None
) extends AhcWSClientConfigExtractorResult {

  // Retain the Play WS 3.0 constructor for compiled callers. Settings added since then use their defaults.
  def this(
      wsClientConfig: WSClientConfig,
      maxConnectionsPerHost: Int,
      maxConnectionsTotal: Int,
      maxConnectionLifetime: Duration,
      idleConnectionInPoolTimeout: Duration,
      connectionPoolCleanerPeriod: Duration,
      maxNumberOfRedirects: Int,
      maxRequestRetry: Int,
      disableUrlEncoding: Boolean,
      keepAlive: Boolean,
      useLaxCookieEncoder: Boolean,
      useCookieStore: Boolean
  ) = this(
    wsClientConfig,
    maxConnectionsPerHost,
    maxConnectionsTotal,
    maxConnectionLifetime,
    idleConnectionInPoolTimeout,
    connectionPoolCleanerPeriod,
    maxNumberOfRedirects,
    maxRequestRetry,
    disableUrlEncoding,
    keepAlive,
    useLaxCookieEncoder,
    useCookieStore,
    http2Enabled = false,
    http2InitialWindowSize = None,
    http2MaxConcurrentStreams = None,
    maxDecompressedResponseSize = None,
    shutdownQuietPeriod = Duration.Zero,
    shutdownTimeout = Duration.Zero,
    refuseSchemeDowngradeOnRedirect = None,
    refuseCrossOriginBodyOnRedirect = None
  )

  def copy(
      wsClientConfig: WSClientConfig = this.wsClientConfig,
      maxConnectionsPerHost: Int = this.maxConnectionsPerHost,
      maxConnectionsTotal: Int = this.maxConnectionsTotal,
      maxConnectionLifetime: Duration = this.maxConnectionLifetime,
      idleConnectionInPoolTimeout: Duration = this.idleConnectionInPoolTimeout,
      connectionPoolCleanerPeriod: Duration = this.connectionPoolCleanerPeriod,
      maxNumberOfRedirects: Int = this.maxNumberOfRedirects,
      maxRequestRetry: Int = this.maxRequestRetry,
      disableUrlEncoding: Boolean = this.disableUrlEncoding,
      keepAlive: Boolean = this.keepAlive,
      useLaxCookieEncoder: Boolean = this.useLaxCookieEncoder,
      useCookieStore: Boolean = this.useCookieStore,
      http2Enabled: Boolean = this.http2Enabled,
      http2InitialWindowSize: Option[Int] = this.http2InitialWindowSize,
      http2MaxConcurrentStreams: Option[Int] = this.http2MaxConcurrentStreams,
      maxDecompressedResponseSize: Option[Long] = this.maxDecompressedResponseSize,
      shutdownQuietPeriod: FiniteDuration = this.shutdownQuietPeriod,
      shutdownTimeout: FiniteDuration = this.shutdownTimeout,
      refuseSchemeDowngradeOnRedirect: Option[Boolean] = this.refuseSchemeDowngradeOnRedirect,
      refuseCrossOriginBodyOnRedirect: Option[Boolean] = this.refuseCrossOriginBodyOnRedirect
  ): AhcWSClientConfig =
    new AhcWSClientConfig(
      wsClientConfig,
      maxConnectionsPerHost,
      maxConnectionsTotal,
      maxConnectionLifetime,
      idleConnectionInPoolTimeout,
      connectionPoolCleanerPeriod,
      maxNumberOfRedirects,
      maxRequestRetry,
      disableUrlEncoding,
      keepAlive,
      useLaxCookieEncoder,
      useCookieStore,
      http2Enabled,
      http2InitialWindowSize,
      http2MaxConcurrentStreams,
      maxDecompressedResponseSize,
      shutdownQuietPeriod,
      shutdownTimeout,
      refuseSchemeDowngradeOnRedirect,
      refuseCrossOriginBodyOnRedirect
    )

  // Retain the Play WS 3.0 copy signature and preserve settings added since then.
  def copy(
      wsClientConfig: WSClientConfig,
      maxConnectionsPerHost: Int,
      maxConnectionsTotal: Int,
      maxConnectionLifetime: Duration,
      idleConnectionInPoolTimeout: Duration,
      connectionPoolCleanerPeriod: Duration,
      maxNumberOfRedirects: Int,
      maxRequestRetry: Int,
      disableUrlEncoding: Boolean,
      keepAlive: Boolean,
      useLaxCookieEncoder: Boolean,
      useCookieStore: Boolean
  ): AhcWSClientConfig =
    new AhcWSClientConfig(
      wsClientConfig,
      maxConnectionsPerHost,
      maxConnectionsTotal,
      maxConnectionLifetime,
      idleConnectionInPoolTimeout,
      connectionPoolCleanerPeriod,
      maxNumberOfRedirects,
      maxRequestRetry,
      disableUrlEncoding,
      keepAlive,
      useLaxCookieEncoder,
      useCookieStore,
      http2Enabled,
      http2InitialWindowSize,
      http2MaxConcurrentStreams,
      maxDecompressedResponseSize,
      shutdownQuietPeriod,
      shutdownTimeout,
      refuseSchemeDowngradeOnRedirect,
      refuseCrossOriginBodyOnRedirect
    )
}

object AhcWSClientConfig extends AhcWSClientConfigExtractor {

  // Retain the Play WS 3.0 companion apply signature for compiled callers.
  def apply(
      wsClientConfig: WSClientConfig,
      maxConnectionsPerHost: Int,
      maxConnectionsTotal: Int,
      maxConnectionLifetime: Duration,
      idleConnectionInPoolTimeout: Duration,
      connectionPoolCleanerPeriod: Duration,
      maxNumberOfRedirects: Int,
      maxRequestRetry: Int,
      disableUrlEncoding: Boolean,
      keepAlive: Boolean,
      useLaxCookieEncoder: Boolean,
      useCookieStore: Boolean
  ): AhcWSClientConfig =
    new AhcWSClientConfig(
      wsClientConfig,
      maxConnectionsPerHost,
      maxConnectionsTotal,
      maxConnectionLifetime,
      idleConnectionInPoolTimeout,
      connectionPoolCleanerPeriod,
      maxNumberOfRedirects,
      maxRequestRetry,
      disableUrlEncoding,
      keepAlive,
      useLaxCookieEncoder,
      useCookieStore
    )
}

/**
 * Factory for creating AhcWSClientConfig, for use from Java.
 */
object AhcWSClientConfigFactory {

  /**
   * Creates a AhcWSClientConfig from a Typesafe Config object.
   *
   * @param config the config file containing settings for WSConfigParser
   * @param classLoader the classloader
   * @return a AhcWSClientConfig configuration object.
   */
  def forConfig(
      config: Config = ConfigFactory.load(),
      classLoader: ClassLoader = this.getClass.getClassLoader
  ): AhcWSClientConfig = {
    val wsClientConfig = new WSConfigParser(config, classLoader).parse()
    new AhcWSClientConfigParser(wsClientConfig, config, classLoader).parse()
  }

  def forClientConfig(config: WSClientConfig = WSClientConfig()): AhcWSClientConfig = {
    AhcWSClientConfig(wsClientConfig = config)
  }
}

/**
 * This class creates a AhcWSClientConfig object from configuration.
 */
@Singleton
class AhcWSClientConfigParser @Inject() (
    wsClientConfig: WSClientConfig,
    configuration: Config,
    classLoader: ClassLoader
) extends Provider[AhcWSClientConfig] {

  def get = parse()

  def parse(): AhcWSClientConfig = {

    def getDuration(key: String, default: Duration) = {
      try {
        Duration(configuration.getString(key))
      } catch {
        case e: ConfigException.Null =>
          default
      }
    }

    def getOptionalInt(key: String): Option[Int] = {
      try {
        Some(configuration.getInt(key))
      } catch {
        case _: ConfigException.Null => None
      }
    }

    def getOptionalBoolean(key: String): Option[Boolean] = {
      try {
        Some(configuration.getBoolean(key))
      } catch {
        case _: ConfigException.Null => None
      }
    }

    def getOptionalBytes(key: String): Option[Long] = {
      try {
        Some(configuration.getMemorySize(key).toBytes)
      } catch {
        case _: ConfigException.Null         => None
        case cause: IllegalArgumentException =>
          throw new ConfigException.BadValue(
            configuration.getValue(key).origin(),
            key,
            "Memory size must be greater than or equal to 0 bytes",
            cause
          )
      }
    }

    def getOptionalPositiveIntBytes(key: String): Option[Int] = {
      getOptionalBytes(key).map { bytes =>
        if (bytes <= 0 || bytes > Int.MaxValue) {
          throw new ConfigException.BadValue(key, s"Must be between 1 byte and ${Int.MaxValue} bytes")
        }
        bytes.toInt
      }
    }

    def getOptionalHttp2MaxConcurrentStreams(key: String): Option[Int] = {
      getOptionalInt(key).map { maximum =>
        if (maximum != -1 && maximum <= 0) {
          throw new ConfigException.BadValue(key, "Must be -1 or greater than 0")
        }
        maximum
      }
    }

    def getFiniteDuration(key: String): FiniteDuration = {
      try {
        FiniteDuration(configuration.getDuration(key).toMillis, MILLISECONDS)
      } catch {
        case _: ArithmeticException => throw new ConfigException.BadValue(key, "Duration is too large")
      }
    }

    def validateShutdownDurations(quietPeriod: FiniteDuration, timeout: FiniteDuration): Unit = {
      if (quietPeriod < Duration.Zero) {
        throw new ConfigException.BadValue("play.ws.ahc.shutdownQuietPeriod", "Must be greater than or equal to 0")
      }
      if (timeout < Duration.Zero) {
        throw new ConfigException.BadValue("play.ws.ahc.shutdownTimeout", "Must be greater than or equal to 0")
      }
      if (timeout < quietPeriod) {
        throw new ConfigException.BadValue(
          "play.ws.ahc.shutdownTimeout",
          "Must be greater than or equal to play.ws.ahc.shutdownQuietPeriod"
        )
      }
    }

    val maximumConnectionsPerHost       = configuration.getInt("play.ws.ahc.maxConnectionsPerHost")
    val maximumConnectionsTotal         = configuration.getInt("play.ws.ahc.maxConnectionsTotal")
    val maxConnectionLifetime           = getDuration("play.ws.ahc.maxConnectionLifetime", Duration.Inf)
    val idleConnectionInPoolTimeout     = getDuration("play.ws.ahc.idleConnectionInPoolTimeout", 1.minute)
    val connectionPoolCleanerPeriod     = getDuration("play.ws.ahc.connectionPoolCleanerPeriod", 1.second)
    val maximumNumberOfRedirects        = configuration.getInt("play.ws.ahc.maxNumberOfRedirects")
    val maxRequestRetry                 = configuration.getInt("play.ws.ahc.maxRequestRetry")
    val disableUrlEncoding              = configuration.getBoolean("play.ws.ahc.disableUrlEncoding")
    val keepAlive                       = configuration.getBoolean("play.ws.ahc.keepAlive")
    val useLaxCookieEncoder             = configuration.getBoolean("play.ws.ahc.useLaxCookieEncoder")
    val useCookieStore                  = configuration.getBoolean("play.ws.ahc.useCookieStore")
    val http2Enabled                    = configuration.getBoolean("play.ws.ahc.http2Enabled")
    val http2InitialWindowSize          = getOptionalPositiveIntBytes("play.ws.ahc.http2InitialWindowSize")
    val http2MaxConcurrentStreams       = getOptionalHttp2MaxConcurrentStreams("play.ws.ahc.http2MaxConcurrentStreams")
    val maxDecompressedResponseSize     = getOptionalBytes("play.ws.ahc.maxDecompressedResponseSize")
    val shutdownQuietPeriod             = getFiniteDuration("play.ws.ahc.shutdownQuietPeriod")
    val shutdownTimeout                 = getFiniteDuration("play.ws.ahc.shutdownTimeout")
    val refuseSchemeDowngradeOnRedirect = getOptionalBoolean("play.ws.ahc.refuseSchemeDowngradeOnRedirect")
    val refuseCrossOriginBodyOnRedirect = getOptionalBoolean("play.ws.ahc.refuseCrossOriginBodyOnRedirect")
    validateShutdownDurations(shutdownQuietPeriod, shutdownTimeout)

    AhcWSClientConfig(
      wsClientConfig = wsClientConfig,
      maxConnectionsPerHost = maximumConnectionsPerHost,
      maxConnectionsTotal = maximumConnectionsTotal,
      maxConnectionLifetime = maxConnectionLifetime,
      idleConnectionInPoolTimeout = idleConnectionInPoolTimeout,
      connectionPoolCleanerPeriod = connectionPoolCleanerPeriod,
      maxNumberOfRedirects = maximumNumberOfRedirects,
      maxRequestRetry = maxRequestRetry,
      disableUrlEncoding = disableUrlEncoding,
      keepAlive = keepAlive,
      useLaxCookieEncoder = useLaxCookieEncoder,
      useCookieStore = useCookieStore,
      http2Enabled = http2Enabled,
      http2InitialWindowSize = http2InitialWindowSize,
      http2MaxConcurrentStreams = http2MaxConcurrentStreams,
      maxDecompressedResponseSize = maxDecompressedResponseSize,
      shutdownQuietPeriod = shutdownQuietPeriod,
      shutdownTimeout = shutdownTimeout,
      refuseSchemeDowngradeOnRedirect = refuseSchemeDowngradeOnRedirect,
      refuseCrossOriginBodyOnRedirect = refuseCrossOriginBodyOnRedirect
    )
  }
}

/**
 * Builds a valid AsyncHttpClientConfig object from config.
 *
 * @param ahcConfig the ahc client configuration.
 */
class AhcConfigBuilder(ahcConfig: AhcWSClientConfig = AhcWSClientConfig()) {

  protected val addCustomSettings: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder =
    identity

  /**
   * The underlying `DefaultAsyncHttpClientConfig.Builder` used by this instance.
   */
  val builder: DefaultAsyncHttpClientConfig.Builder = new DefaultAsyncHttpClientConfig.Builder()

  private[ahc] val logger        = LoggerFactory.getLogger(this.getClass.getName)
  private[ahc] val loggerFactory = new AhcLoggerFactory(LoggerFactory.getILoggerFactory)

  /**
   * Configure the underlying builder with values specified by the `config`, and add any custom settings.
   *
   * @return the resulting builder
   */
  def configure(): DefaultAsyncHttpClientConfig.Builder = {
    val config = ahcConfig.wsClientConfig

    configureWS(ahcConfig)

    configureSSL(config.ssl)

    addCustomSettings(builder)
  }

  /**
   * Configure and build the `AsyncHttpClientConfig` based on the settings provided
   *
   * @return the resulting builder
   */
  def build(): AsyncHttpClientConfig = {
    configure().build()
  }

  /**
   * Modify the underlying `DefaultAsyncHttpClientConfig.Builder` using the provided function, after defaults are set.
   *
   * @param modify function with custom settings to apply to this builder before the client is built
   * @return the new builder
   */
  def modifyUnderlying(
      modify: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder
  ): AhcConfigBuilder = {
    new AhcConfigBuilder(ahcConfig) {
      override val addCustomSettings = modify.compose(AhcConfigBuilder.this.addCustomSettings)
      override val builder           = AhcConfigBuilder.this.builder
    }
  }

  /**
   * Configures the global settings.
   */
  def configureWS(ahcConfig: AhcWSClientConfig): Unit = {
    val config = ahcConfig.wsClientConfig

    require(ahcConfig.http2InitialWindowSize.forall(_ > 0), "http2InitialWindowSize must be greater than 0")
    require(
      ahcConfig.http2MaxConcurrentStreams.forall(maximum => maximum == -1 || maximum > 0),
      "http2MaxConcurrentStreams must be -1 or greater than 0"
    )
    require(
      ahcConfig.maxDecompressedResponseSize.forall(_ >= 0),
      "maxDecompressedResponseSize must be greater than or equal to 0"
    )
    require(ahcConfig.shutdownQuietPeriod >= Duration.Zero, "shutdownQuietPeriod must be greater than or equal to 0")
    require(ahcConfig.shutdownTimeout >= Duration.Zero, "shutdownTimeout must be greater than or equal to 0")
    require(
      ahcConfig.shutdownTimeout >= ahcConfig.shutdownQuietPeriod,
      "shutdownTimeout must be greater than or equal to shutdownQuietPeriod"
    )

    def toJavaDuration(duration: Duration): JDuration = {
      if (duration.isFinite) JDuration.ofMillis(duration.toMillis)
      else JDuration.ofMillis(-1)
    }

    builder
      .setConnectTimeout(toJavaDuration(config.connectionTimeout))
      .setReadTimeout(toJavaDuration(config.idleTimeout))
      .setRequestTimeout(toJavaDuration(config.requestTimeout))
      .setFollowRedirect(config.followRedirects)
      .setUseProxyProperties(config.useProxyProperties)
      .setCompressionEnforced(config.compressionEnabled)

    config.userAgent.foreach(builder.setUserAgent)

    builder.setMaxConnectionsPerHost(ahcConfig.maxConnectionsPerHost)
    builder.setMaxConnections(ahcConfig.maxConnectionsTotal)
    builder.setHttp2Enabled(ahcConfig.http2Enabled)
    ahcConfig.http2InitialWindowSize.foreach(builder.setHttp2InitialWindowSize)
    ahcConfig.http2MaxConcurrentStreams.foreach(builder.setHttp2MaxConcurrentStreams)
    ahcConfig.maxDecompressedResponseSize.foreach { maximum =>
      builder.setMaxDecompressedResponseSize(maximum)
      builder.setHttp2MaxDecompressedResponseSize(maximum)
    }
    builder.setConnectionTtl(toJavaDuration(ahcConfig.maxConnectionLifetime))
    builder.setPooledConnectionIdleTimeout(toJavaDuration(ahcConfig.idleConnectionInPoolTimeout))
    builder.setConnectionPoolCleanerPeriod(toJavaDuration(ahcConfig.connectionPoolCleanerPeriod))
    builder.setMaxRedirects(ahcConfig.maxNumberOfRedirects)
    builder.setMaxRequestRetry(ahcConfig.maxRequestRetry)
    builder.setDisableUrlEncodingForBoundRequests(ahcConfig.disableUrlEncoding)
    builder.setKeepAlive(ahcConfig.keepAlive)
    builder.setShutdownQuietPeriod(toJavaDuration(ahcConfig.shutdownQuietPeriod))
    builder.setShutdownTimeout(toJavaDuration(ahcConfig.shutdownTimeout))
    ahcConfig.refuseSchemeDowngradeOnRedirect.foreach(builder.setRefuseSchemeDowngradeOnRedirect)
    ahcConfig.refuseCrossOriginBodyOnRedirect.foreach(builder.setRefuseCrossOriginBodyOnRedirect)
    builder.setUseLaxCookieEncoder(ahcConfig.useLaxCookieEncoder)

    if (!ahcConfig.useCookieStore) {
      builder.setCookieStore(null)
    }
  }

  def configureProtocols(existingProtocols: Array[String], sslConfig: SSLConfigSettings): Array[String] = {
    val definedProtocols = sslConfig.enabledProtocols match {
      case Some(configuredProtocols) =>
        // If we are given a specific list of protocols, then return it in exactly that order,
        // assuming that it's actually possible in the SSL context.
        configuredProtocols.filter(existingProtocols.contains).toArray

      case None =>
        // Otherwise, we return the default protocols in the given list.
        Protocols.recommendedProtocols.filter(existingProtocols.contains).toArray
    }

    definedProtocols
  }

  def configureCipherSuites(existingCiphers: Array[String], sslConfig: SSLConfigSettings): Array[String] = {
    val definedCiphers = sslConfig.enabledCipherSuites match {
      case Some(configuredCiphers) =>
        // If we are given a specific list of ciphers, return it in that order.
        configuredCiphers.filter(existingCiphers.contains(_)).toArray

      case None =>
        existingCiphers
    }

    definedCiphers
  }

  /**
   * Configures the SSL.  Can use the system SSLContext.getDefault() if "ws.ssl.default" is set.
   */
  def configureSSL(sslConfig: SSLConfigSettings): Unit = {

    // context!
    val sslContext = if (sslConfig.loose.acceptAnyCertificate) {
      // Accepts any certificate for any host, but still presents the configured client certificates.
      // Never use this in production.
      buildLooseSSLContext(sslConfig)
    } else if (sslConfig.default) {
      logger.info("buildSSLContext: play.ws.ssl.default is true, using default SSLContext")
      SSLContext.getDefault
    } else {
      // break out the static methods as much as we can...
      val keyManagerFactory   = buildKeyManagerFactory(sslConfig)
      val trustManagerFactory = buildTrustManagerFactory(sslConfig)
      new ConfigSSLContextBuilder(loggerFactory, sslConfig, keyManagerFactory, trustManagerFactory).build()
    }

    // protocols!
    val defaultParams      = sslContext.getDefaultSSLParameters
    val defaultProtocols   = defaultParams.getProtocols
    val protocols          = configureProtocols(defaultProtocols, sslConfig)
    val requestedProtocols = sslConfig.enabledProtocols.getOrElse(Protocols.recommendedProtocols.toSeq)
    require(
      protocols.nonEmpty,
      s"None of the TLS protocols ${requestedProtocols.mkString("[", ", ", "]")} is enabled in the SSL context. " +
        s"Enabled protocols: ${defaultProtocols.mkString("[", ", ", "]")}"
    )
    builder.setEnabledProtocols(protocols)

    // ciphers!
    val defaultCiphers        = defaultParams.getCipherSuites
    val cipherSuites          = configureCipherSuites(defaultCiphers, sslConfig)
    val requestedCipherSuites = sslConfig.enabledCipherSuites.getOrElse(Nil)
    require(
      cipherSuites.nonEmpty,
      s"None of the cipher suites ${requestedCipherSuites.mkString("[", ", ", "]")} is enabled in the SSL context"
    )
    builder.setEnabledCipherSuites(cipherSuites)

    builder.setUseInsecureTrustManager(sslConfig.loose.acceptAnyCertificate)
    builder.setSslEngineFactory(
      new ConfiguredSslEngineFactory(sslContext, loose = sslConfig.loose.acceptAnyCertificate)
    )
  }

  private def buildLooseSSLContext(sslConfig: SSLConfigSettings): SSLContext = {
    val contextBuilder = new ConfigSSLContextBuilder(
      loggerFactory,
      sslConfig,
      buildKeyManagerFactory(sslConfig),
      buildTrustManagerFactory(sslConfig)
    )
    val keyManagers =
      if (sslConfig.keyManagerConfig.keyStoreConfigs.nonEmpty)
        Seq(contextBuilder.buildCompositeKeyManager(sslConfig.keyManagerConfig, sslConfig.debug))
      else Nil
    val trustManagers = InsecureTrustManagerFactory.INSTANCE.getTrustManagers.toSeq
    contextBuilder.buildSSLContext(sslConfig.protocol, keyManagers, trustManagers, sslConfig.secureRandom)
  }

  def buildKeyManagerFactory(ssl: SSLConfigSettings): KeyManagerFactoryWrapper = {
    new DefaultKeyManagerFactoryWrapper(ssl.keyManagerConfig.algorithm)
  }

  def buildTrustManagerFactory(ssl: SSLConfigSettings): TrustManagerFactoryWrapper = {
    new DefaultTrustManagerFactoryWrapper(ssl.trustManagerConfig.algorithm)
  }
}
