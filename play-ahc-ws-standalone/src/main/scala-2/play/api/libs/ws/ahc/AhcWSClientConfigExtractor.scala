/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration.Duration

/**
 * Preserves the Play WS 3.0 Scala 2 extractor signature: an `Option` containing the original 12 fields.
 *
 * This intentionally differs from the Scala 3 implementation, whose 3.0 compiler-generated `unapply` returned the
 * config itself for name-based extraction. Unifying the two implementations would break one platform's compiled
 * pattern matches even though both extract the same fields.
 */
private[ahc] trait AhcWSClientConfigExtractor {
  def unapply(
      config: AhcWSClientConfig
  ): Option[
    (
        WSClientConfig,
        Int,
        Int,
        Duration,
        Duration,
        Duration,
        Int,
        Int,
        Boolean,
        Boolean,
        Boolean,
        Boolean
    )
  ] = {
    Option(config).map { value =>
      (
        value.wsClientConfig,
        value.maxConnectionsPerHost,
        value.maxConnectionsTotal,
        value.maxConnectionLifetime,
        value.idleConnectionInPoolTimeout,
        value.connectionPoolCleanerPeriod,
        value.maxNumberOfRedirects,
        value.maxRequestRetry,
        value.disableUrlEncoding,
        value.keepAlive,
        value.useLaxCookieEncoder,
        value.useCookieStore
      )
    }
  }
}

private[ahc] trait AhcWSClientConfigExtractorResult
