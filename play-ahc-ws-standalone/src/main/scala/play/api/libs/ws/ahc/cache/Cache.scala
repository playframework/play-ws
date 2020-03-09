/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import scala.concurrent.Future

/**
 * A very simple cache trait.
 *
 * Implementations can write adapters that map through to this trait, i.e.
 *
 * {{{
 * class CaffeineHttpCache extends Cache {
 *    val underlying = Caffeine.newBuilder()
 *      .ticker(Ticker.systemTicker())
 *      .expireAfterWrite(365, TimeUnit.DAYS)
 *      .build[EffectiveURIKey, ResponseEntry]()
 *
 *     override def remove(key: EffectiveURIKey) = Future.successful(Option(underlying.invalidate(key))
 *     override def put(key: EffectiveURIKey, entry: ResponseEntry) = Future.successful(underlying.put(key, entry))
 *     override def get(key: EffectiveURIKey) = Future.successful(underlying.getIfPresent(key))
 *     override def close(): Unit = underlying.cleanUp()
 * }
 * }}}
 */
trait Cache {

  def get(key: EffectiveURIKey): Future[Option[ResponseEntry]]

  def put(key: EffectiveURIKey, entry: ResponseEntry): Future[Unit]

  def remove(key: EffectiveURIKey): Future[Unit]

  def close(): Unit

}
