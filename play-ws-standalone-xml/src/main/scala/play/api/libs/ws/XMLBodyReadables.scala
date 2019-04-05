/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import java.io.ByteArrayInputStream

import scala.xml.InputSource

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
    xml.XML.load(new InputSource(new ByteArrayInputStream(response.bodyAsBytes.toArray)))
  }

}

object XMLBodyReadables extends XMLBodyReadables
