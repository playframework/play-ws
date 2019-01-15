/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

import scala.collection.immutable.TreeMap

/**
 * Useful mapping code.
 */
trait AhcUtilities {

  def headersToMap(headers: HttpHeaders): TreeMap[String, Seq[String]] = {
    import scala.collection.JavaConverters._
    val mutableMap = scala.collection.mutable.HashMap[String, Seq[String]]()
    headers.names().asScala.foreach { name =>
      mutableMap.put(name, headers.getAll(name).asScala)
    }
    TreeMap[String, Seq[String]]()(CaseInsensitiveOrdered) ++ mutableMap
  }

}
