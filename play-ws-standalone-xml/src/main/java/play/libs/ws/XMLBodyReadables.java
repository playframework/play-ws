/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;

/**
 *
 */
public interface XMLBodyReadables {

    default BodyReadable<Document> xml() {
        return response -> XML.fromInputSource(new InputSource(new ByteArrayInputStream(response.getBodyAsBytes().toArray())));
    }

}
