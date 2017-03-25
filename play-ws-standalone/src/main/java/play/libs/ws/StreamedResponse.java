/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * A streamed response containing a response header and a streamable body.
 */
public interface StreamedResponse {

    WSResponseHeaders getHeaders();

    Source<ByteString, ?> getBody();
}
