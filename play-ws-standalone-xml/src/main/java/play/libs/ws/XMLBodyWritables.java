/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.apache.pekko.util.ByteString;
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
