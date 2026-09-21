/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

/**
 * Case Insensitive Ordering. We first compare by length, then
 * use a case insensitive lexicographic order. This allows us to
 * use a much faster length comparison before we even start looking
 * at the content of the strings.
 */
private[ahc] object CaseInsensitiveOrdered extends Ordering[String] {
  // Keeping the Ordering implicit here works with the different TreeMap context-parameter
  // layouts seen by Scala 2.13/3.3 and Scala 3.9+.
  def empty[V]: scala.collection.immutable.TreeMap[String, V] = {
    implicit val ordering: Ordering[String] = this
    scala.collection.immutable.TreeMap.empty[String, V]
  }

  def emptyMutable[V]: scala.collection.mutable.TreeMap[String, V] = {
    implicit val ordering: Ordering[String] = this
    scala.collection.mutable.TreeMap.empty[String, V]
  }

  def compare(x: String, y: String): Int = {
    val xl = x.length
    val yl = y.length
    if (xl < yl) -1 else if (xl > yl) 1 else x.compareToIgnoreCase(y)
  }
}
