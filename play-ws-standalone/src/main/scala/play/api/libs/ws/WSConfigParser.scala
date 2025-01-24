/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.sslconfig.ssl.SSLConfigParser
import com.typesafe.sslconfig.util.EnrichedConfig

import scala.concurrent.duration.Duration

/**
 * This class creates a WSClientConfig object from a Typesafe Config object.
 *
 * You can create a client config from an application.conf file by running
 *
 * {{{
 * import play.api.libs.ws.WSConfigParser
 * import com.typesafe.config.ConfigFactory
 *
 * val wsClientConfig = new WSConfigParser(
 *   ConfigFactory.load(), this.getClass.getClassLoader).parse()
 * }}}
 */
@Singleton
class WSConfigParser @Inject() (config: Config, classLoader: ClassLoader) extends Provider[WSClientConfig] {

  def parse(): WSClientConfig = {
    val wsConfig = config.getConfig("play.ws")

    val connectionTimeout = Duration(wsConfig.getString("timeout.connection"))
    val idleTimeout       = Duration(wsConfig.getString("timeout.idle"))
    val requestTimeout    = Duration(wsConfig.getString("timeout.request"))

    val followRedirects    = wsConfig.getBoolean("followRedirects")
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
      ssl = sslConfig
    )
  }

  override lazy val get: WSClientConfig = parse()
}
