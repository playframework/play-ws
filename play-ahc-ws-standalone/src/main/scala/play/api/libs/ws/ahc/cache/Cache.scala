/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws.ahc.cache

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
 *     override def remove(key: EffectiveURIKey): Unit = underlying.invalidate(key)
 *     override def put(key: EffectiveURIKey, entry: ResponseEntry): Unit = underlying.put(key, entry)
 *     override def get(key: EffectiveURIKey): ResponseEntry = underlying.getIfPresent(key)
 *     override def close(): Unit = underlying.cleanUp()
 * }
 * }}}
 */
trait Cache {

  def get(key: EffectiveURIKey): ResponseEntry

  def put(key: EffectiveURIKey, entry: ResponseEntry): Unit

  def remove(key: EffectiveURIKey): Unit

  def close(): Unit

}
