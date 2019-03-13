/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

import akka.util.ByteString
import org.w3c.dom.Document

/**
 *
 */
trait XMLBodyWritables {

  /**
   * Creates an InMemoryBody with "text/xml" content type.
   */
  implicit def writeableOf_NodeSeq[C <: scala.xml.NodeSeq]: BodyWritable[C] = {
    BodyWritable(xml => InMemoryBody(ByteString.fromString(xml.toString())), "text/xml")
  }

  /**
   * Creates an InMemoryBody with "text/xml" content type.
   */
  implicit val writeableOf_NodeBuffer: BodyWritable[scala.xml.NodeBuffer] = {
    BodyWritable(xml => InMemoryBody(ByteString.fromString(xml.toString())), "text/xml")
  }

  /**
   * Creates an InMemoryBody with "text/xml" content type.
   */
  implicit val writeableOf_Document: BodyWritable[Document] = {
    BodyWritable(xml => InMemoryBody(play.libs.ws.XML.toBytes(xml)), "text/xml")
  }

}

object XMLBodyWritables extends XMLBodyWritables
