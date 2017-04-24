/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.api.libs.ws.ahc.cache

import play.shaded.ahc.io.netty.handler.codec.http.{ DefaultHttpHeaders, HttpHeaders }
import play.shaded.ahc.org.asynchttpclient.{ Request, RequestBuilder }

import scala.collection.mutable

/**
 * Utility methods to make building requests and responses easier.
 */
trait CacheBuilderMethods {

  def generateCache: AhcHttpCache = {
    AhcHttpCache(new StubHttpCache())
  }

  def generateRequest(url: String)(block: HttpHeaders => HttpHeaders): Request = {
    val requestBuilder = new RequestBuilder()
    val requestHeaders = block(new DefaultHttpHeaders())

    requestBuilder
      .setUrl(url)
      .setHeaders(requestHeaders)
      .build
  }

}

class StubHttpCache extends Cache {

  private val underlying = new mutable.HashMap[EffectiveURIKey, ResponseEntry]()

  override def remove(key: EffectiveURIKey): Unit = underlying.remove(key)

  override def put(key: EffectiveURIKey, entry: ResponseEntry): Unit = underlying.put(key, entry)

  override def get(key: EffectiveURIKey): ResponseEntry = underlying.get(key).orNull

  override def close(): Unit = {

  }

}
