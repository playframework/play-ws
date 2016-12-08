package play.api.libs.ws

import play.api.Play

import scala.xml.Elem

trait WSResponse extends StandaloneWSResponse {

  /**
   * The response body as Xml.
   */
  lazy val xml: Elem = Play.XML.loadString(body)

}
