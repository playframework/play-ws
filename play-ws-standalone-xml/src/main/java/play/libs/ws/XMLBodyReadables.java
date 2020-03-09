/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import org.w3c.dom.Document;

/**
 *
 */
public interface XMLBodyReadables {

    default BodyReadable<Document> xml() {
        return response -> XML.fromString(response.getBody());
    }

}
