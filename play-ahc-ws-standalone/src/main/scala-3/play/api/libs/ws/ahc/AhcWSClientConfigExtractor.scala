/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration.Duration

/**
 * Preserves the Play WS 3.0 Scala 3 extractor signature, whose compiler-generated `unapply` returned the config
 * itself for name-based extraction.
 *
 * This intentionally differs from the Scala 2 implementation, which must return an `Option` containing a 12-tuple.
 * Unifying the two implementations would break one platform's compiled pattern matches.
 */
private[ahc] trait AhcWSClientConfigExtractor {
  def unapply(config: AhcWSClientConfig): AhcWSClientConfig = config
}

/** Compatibility-only name-based extractor members required by the Play WS 3.0 Scala 3 signature. */
private[ahc] trait AhcWSClientConfigExtractorResult { self: AhcWSClientConfig =>

  /** Required by Scala 3's name-based extractor protocol. */
  def isEmpty: Boolean = false

  /** Returns only the 12 fields exposed by the Play WS 3.0 extractor. */
  def get: (
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
  ) = {
    (
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
}
