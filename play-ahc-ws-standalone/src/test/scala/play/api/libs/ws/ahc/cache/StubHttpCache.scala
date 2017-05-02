/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.ahc.cache

import scala.collection.mutable

class StubHttpCache extends Cache {

  private val underlying = new mutable.HashMap[EffectiveURIKey, ResponseEntry]()

  override def remove(key: EffectiveURIKey): Unit = underlying.remove(key)

  override def put(key: EffectiveURIKey, entry: ResponseEntry): Unit = underlying.put(key, entry)

  override def get(key: EffectiveURIKey): ResponseEntry = underlying.get(key).orNull

  override def close(): Unit = {

  }

}
