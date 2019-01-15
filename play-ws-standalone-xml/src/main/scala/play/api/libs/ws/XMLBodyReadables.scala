/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

trait XMLBodyReadables {

  import scala.xml.Elem
  /**
   * Converts a response body into XML document:
   *
   * {{{
   * val xml = response.body[scala.xml.Elem]
   * }}}
   */
  implicit val readableAsXml: BodyReadable[Elem] = BodyReadable { response =>
    XML.parser.loadString(response.body)
  }

}

object XMLBodyReadables extends XMLBodyReadables
