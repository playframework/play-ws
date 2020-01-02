/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
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
   * import scala.xml.Elem
   *
   * import play.api.libs.ws.StandaloneWSResponse
   * import play.api.libs.ws.XMLBodyReadables._
   *
   * def foo(resp: StandaloneWSResponse): Elem = resp.body[Elem]
   * }}}
   */
  implicit val readableAsXml: BodyReadable[Elem] = BodyReadable { response =>
    xml.XML.load(new InputSource(new ByteArrayInputStream(response.bodyAsBytes.toArray)))
  }

}

object XMLBodyReadables extends XMLBodyReadables
