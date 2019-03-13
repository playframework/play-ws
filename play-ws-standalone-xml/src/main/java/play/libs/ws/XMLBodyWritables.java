/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import akka.util.ByteString;
import org.w3c.dom.Document;

/**
 *
 */
public interface XMLBodyWritables {

    /**
     * Creates a {@link InMemoryBodyWritable} for JSON, setting the content-type to "application/json".
     *
     * @param document the node to pass in.
     * @return a {@link InMemoryBodyWritable} instance.
     */
    default BodyWritable<ByteString> body(Document document) {
        return new InMemoryBodyWritable(XML.toBytes(document), "application/xml");
    }

}
