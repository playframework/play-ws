/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

/**
 * Case Insensitive Ordering. We first compare by length, then
 * use a case insensitive lexicographic order. This allows us to
 * use a much faster length comparison before we even start looking
 * at the content of the strings.
 */
private[ahc] object CaseInsensitiveOrdered extends Ordering[String] {
  def compare(x: String, y: String): Int = {
    val xl = x.length
    val yl = y.length
    if (xl < yl) -1 else if (xl > yl) 1 else x.compareToIgnoreCase(y)
  }
}
