/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.io.Closeable
import java.io.IOException

/**
 * The WSClient holds the configuration information needed to build a request, and provides a way to get a request holder.
 */
trait StandaloneWSClient extends Closeable {

  /**
   * The underlying implementation of the client, if any.  You must cast explicitly to the type you want.
   *
   * @tparam T the type you are expecting (i.e. isInstanceOf)
   * @return the backing class.
   */
  def underlying[T]: T

  /**
   * Generates a request.  Throws IllegalArgumentException if the URL is invalid.
   *
   * @param url The base URL to make HTTP requests to.
   * @return a request
   */
  @throws[IllegalArgumentException]
  def url(url: String): StandaloneWSRequest

  /**
   * Closes this client, and releases underlying resources.
   */
  @throws[IOException]
  def close(): Unit
}
