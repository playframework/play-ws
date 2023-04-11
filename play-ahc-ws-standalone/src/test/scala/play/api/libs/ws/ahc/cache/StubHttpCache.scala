/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import scala.collection.mutable
import scala.concurrent.Future

class StubHttpCache extends Cache {

  private val underlying = new mutable.HashMap[EffectiveURIKey, ResponseEntry]()

  override def remove(key: EffectiveURIKey): Future[Unit] = Future.successful(underlying.remove(key))

  override def put(key: EffectiveURIKey, entry: ResponseEntry): Future[Unit] =
    Future.successful(underlying.put(key, entry))

  override def get(key: EffectiveURIKey): Future[Option[ResponseEntry]] = Future.successful(underlying.get(key))

  override def close(): Unit = {}

}
