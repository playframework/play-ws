/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

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
import play.shaded.ahc.io.netty.handler.ssl.SslContextBuilder
import play.shaded.ahc.io.netty.handler.ssl.util.InsecureTrustManagerFactory
import play.shaded.ahc.org.asynchttpclient.netty.ssl.JsseSslEngineFactory
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
    useCookieStore: Boolean = false
)

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

    val maximumConnectionsPerHost   = configuration.getInt("play.ws.ahc.maxConnectionsPerHost")
    val maximumConnectionsTotal     = configuration.getInt("play.ws.ahc.maxConnectionsTotal")
    val maxConnectionLifetime       = getDuration("play.ws.ahc.maxConnectionLifetime", Duration.Inf)
    val idleConnectionInPoolTimeout = getDuration("play.ws.ahc.idleConnectionInPoolTimeout", 1.minute)
    val connectionPoolCleanerPeriod = getDuration("play.ws.ahc.connectionPoolCleanerPeriod", 1.second)
    val maximumNumberOfRedirects    = configuration.getInt("play.ws.ahc.maxNumberOfRedirects")
    val maxRequestRetry             = configuration.getInt("play.ws.ahc.maxRequestRetry")
    val disableUrlEncoding          = configuration.getBoolean("play.ws.ahc.disableUrlEncoding")
    val keepAlive                   = configuration.getBoolean("play.ws.ahc.keepAlive")
    val useLaxCookieEncoder         = configuration.getBoolean("play.ws.ahc.useLaxCookieEncoder")
    val useCookieStore              = configuration.getBoolean("play.ws.ahc.useCookieStore")

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
      useCookieStore = useCookieStore
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

    def toMillis(duration: Duration): Int = {
      if (duration.isFinite) duration.toMillis.toInt
      else -1
    }

    builder
      .setConnectTimeout(toMillis(config.connectionTimeout))
      .setReadTimeout(toMillis(config.idleTimeout))
      .setRequestTimeout(toMillis(config.requestTimeout))
      .setFollowRedirect(config.followRedirects)
      .setUseProxyProperties(config.useProxyProperties)
      .setCompressionEnforced(config.compressionEnabled)

    config.userAgent.foreach(builder.setUserAgent)

    builder.setMaxConnectionsPerHost(ahcConfig.maxConnectionsPerHost)
    builder.setMaxConnections(ahcConfig.maxConnectionsTotal)
    builder.setConnectionTtl(toMillis(ahcConfig.maxConnectionLifetime))
    builder.setPooledConnectionIdleTimeout(toMillis(ahcConfig.idleConnectionInPoolTimeout))
    builder.setConnectionPoolCleanerPeriod(toMillis(ahcConfig.connectionPoolCleanerPeriod))
    builder.setMaxRedirects(ahcConfig.maxNumberOfRedirects)
    builder.setMaxRequestRetry(ahcConfig.maxRequestRetry)
    builder.setDisableUrlEncodingForBoundRequests(ahcConfig.disableUrlEncoding)
    builder.setKeepAlive(ahcConfig.keepAlive)
    // forcing shutdown of the AHC event loop because otherwise the test suite fails with a
    // OutOfMemoryException: cannot create new native thread. This is because when executing
    // tests in parallel there can be many threads pool that are left around because AHC is
    // shutting them down gracefully.
    // The proper solution is to make these parameters configurable, so that they can be set
    // to 0 when running tests, and keep sensible defaults otherwise. AHC defaults are
    // shutdownQuiet=2000 (milliseconds) and shutdownTimeout=15000 (milliseconds).
    builder.setShutdownQuietPeriod(0)
    builder.setShutdownTimeout(0)
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
    val sslContext = if (sslConfig.default) {
      logger.info("buildSSLContext: play.ws.ssl.default is true, using default SSLContext")
      SSLContext.getDefault
    } else {
      // break out the static methods as much as we can...
      val keyManagerFactory   = buildKeyManagerFactory(sslConfig)
      val trustManagerFactory = buildTrustManagerFactory(sslConfig)
      new ConfigSSLContextBuilder(loggerFactory, sslConfig, keyManagerFactory, trustManagerFactory).build()
    }

    // protocols!
    val defaultParams    = sslContext.getDefaultSSLParameters
    val defaultProtocols = defaultParams.getProtocols
    val protocols        = configureProtocols(defaultProtocols, sslConfig)
    defaultParams.setProtocols(protocols)
    builder.setEnabledProtocols(protocols)

    // ciphers!
    val defaultCiphers = defaultParams.getCipherSuites
    val cipherSuites   = configureCipherSuites(defaultCiphers, sslConfig)
    defaultParams.setCipherSuites(cipherSuites)
    builder.setEnabledCipherSuites(cipherSuites)

    builder.setUseInsecureTrustManager(sslConfig.loose.acceptAnyCertificate)

    // If you wan't to accept any certificate you also want to use a loose netty based loose SslContext
    // Never use this in production.
    if (sslConfig.loose.acceptAnyCertificate) {
      builder.setSslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build())
    } else {
      builder.setSslEngineFactory(new JsseSslEngineFactory(sslContext))
    }
  }

  def buildKeyManagerFactory(ssl: SSLConfigSettings): KeyManagerFactoryWrapper = {
    new DefaultKeyManagerFactoryWrapper(ssl.keyManagerConfig.algorithm)
  }

  def buildTrustManagerFactory(ssl: SSLConfigSettings): TrustManagerFactoryWrapper = {
    new DefaultTrustManagerFactoryWrapper(ssl.trustManagerConfig.algorithm)
  }
}
