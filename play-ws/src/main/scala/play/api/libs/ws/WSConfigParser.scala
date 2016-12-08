package play.api.libs.ws

import javax.inject.{Inject, Provider, Singleton}

import com.typesafe.sslconfig.ssl.SSLConfigParser
import com.typesafe.sslconfig.util.EnrichedConfig
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration

/**
 * This class creates a DefaultWSClientConfig object from the play.api.Configuration.
 */
@Singleton
class WSConfigParser @Inject() (configuration: Configuration, environment: Environment) extends Provider[WSClientConfig] {

  def get = parse()

  def parse(): WSClientConfig = {

    val config = configuration.getDeprecatedWithFallback("play.ws", "ws")

    val connectionTimeout = config.get[Duration]("timeout.connection")
    val idleTimeout = config.get[Duration]("timeout.idle")
    val requestTimeout = config.get[Duration]("timeout.request")

    val followRedirects = config.get[Boolean]("followRedirects")
    val useProxyProperties = config.get[Boolean]("useProxyProperties")

    val userAgent = config.get[Option[String]]("useragent")

    val compressionEnabled = config.get[Boolean]("compressionEnabled")

    val sslConfig = new SSLConfigParser(EnrichedConfig(config.get[Configuration]("ssl").underlying), environment.classLoader).parse()

    WSClientConfig(
      connectionTimeout = connectionTimeout,
      idleTimeout = idleTimeout,
      requestTimeout = requestTimeout,
      followRedirects = followRedirects,
      useProxyProperties = useProxyProperties,
      userAgent = userAgent,
      compressionEnabled = compressionEnabled,
      ssl = sslConfig)
  }
}
