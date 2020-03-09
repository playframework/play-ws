/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

import scala.xml.Elem
import scala.xml.factory.XMLLoader

/**
 *
 */
object XML {
  import play.libs.ws.XML.Constants

  /*
   * We want control over the sax parser used so we specify the factory required explicitly. We know that
   * SAXParserFactoryImpl will yield a SAXParser having looked at its source code, despite there being
   * no explicit doco stating this is the case. That said, there does not appear to be any other way than
   * declaring a factory in order to yield a parser of a specific type.
   */
  private[play] val xercesSaxParserFactory = SAXParserFactory.newInstance()
  xercesSaxParserFactory.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
  xercesSaxParserFactory.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
  xercesSaxParserFactory.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.DISALLOW_DOCTYPE_DECL_FEATURE, true)
  xercesSaxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)

  /*
   * A parser to be used that is configured to ensure that no schemas are loaded.
   */
  def parser: XMLLoader[Elem] = scala.xml.XML.withSAXParser(xercesSaxParserFactory.newSAXParser())

}
