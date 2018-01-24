/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.sslconfig.ssl.SSLConfigSettings

import scala.concurrent.duration._

/**
 * WS client config
 *
 * @param connectionTimeout The maximum time to wait when connecting to the remote host (default is 120 seconds).
 * @param idleTimeout The maximum time the request can stay idle (connection is established but waiting for more data) (default is 120 seconds).
 * @param requestTimeout The total time you accept a request to take (it will be interrupted even if the remote host is still sending data) (default is 120 seconds).
 * @param followRedirects Configures the client to follow 301 and 302 redirects (default is true).
 * @param useProxyProperties To use the JVM system’s HTTP proxy settings (http.proxyHost, http.proxyPort) (default is true).
 * @param userAgent  To configure the User-Agent header field (default is None).
 * @param compressionEnabled Set it to true to use gzip/deflater encoding (default is false).
 * @param ssl use custom SSL / TLS configuration, see https://typesafehub.github.io/ssl-config/ for documentation.
 */
case class WSClientConfig(
  connectionTimeout: Duration = 2.minutes,
  idleTimeout: Duration = 2.minutes,
  requestTimeout: Duration = 2.minutes,
  followRedirects: Boolean = true,
  useProxyProperties: Boolean = true,
  userAgent: Option[String] = None,
  compressionEnabled: Boolean = false,
  ssl: SSLConfigSettings = SSLConfigSettings())

object WSClientConfig {

  /**
   * Creates WSClientConfig from the default application configuration.
   *
   * @return a WSClientConfig configuration object.
   */
  def forConfig(): WSClientConfig =
    forConfig(ConfigFactory.load(), this.getClass.getClassLoader)

  /**
   * Creates a WSClientConfig from a Typesafe Config object.
   *
   * @param config the config file containing settings for WSConfigParser
   * @param classLoader the classloader
   * @return a WSClientConfig configuration object.
   */
  def forConfig(config: Config, classLoader: ClassLoader): WSClientConfig =
    new WSConfigParser(config, classLoader).parse()
}

