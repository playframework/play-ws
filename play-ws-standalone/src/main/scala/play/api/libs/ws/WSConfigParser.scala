/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws

import javax.inject.{ Inject, Provider, Singleton }

import com.typesafe.config.{ Config, ConfigException }
import com.typesafe.sslconfig.ssl.SSLConfigParser
import com.typesafe.sslconfig.util.EnrichedConfig

import scala.concurrent.duration.Duration

/**
 * This class creates a WSClientConfig object from a Typesafe Config object.
 *
 * You can create a client config from an application.conf file by running
 *
 * {{{
 *   val wsClientConfig = new WSConfigParser(ConfigFactory.load(), this.classLoader).parse()
 * }}}
 */
@Singleton
class WSConfigParser @Inject() (config: Config, classLoader: ClassLoader) extends Provider[WSClientConfig] {

  def parse(): WSClientConfig = {
    val wsConfig = config.getConfig("play.ws")

    val connectionTimeout = Duration(wsConfig.getString("timeout.connection"))
    val idleTimeout = Duration(wsConfig.getString("timeout.idle"))
    val requestTimeout = Duration(wsConfig.getString("timeout.request"))

    val followRedirects = wsConfig.getBoolean("followRedirects")
    val useProxyProperties = wsConfig.getBoolean("useProxyProperties")

    val userAgent = {
      try {
        Some(wsConfig.getString("useragent"))
      } catch {
        case e: ConfigException.Null =>
          None
      }
    }

    val compressionEnabled = wsConfig.getBoolean("compressionEnabled")

    val sslConfig = new SSLConfigParser(EnrichedConfig(wsConfig.getConfig("ssl")), classLoader).parse()

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

  override lazy val get: WSClientConfig = parse()
}
