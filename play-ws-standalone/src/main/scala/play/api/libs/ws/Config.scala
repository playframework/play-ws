/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws

import com.typesafe.sslconfig.ssl.SSLConfigSettings

import scala.concurrent.duration._

/**
 * WS client config
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


