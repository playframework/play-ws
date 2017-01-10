/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import java.util.List;
import java.util.Map;

public interface WSResponseHeaders {
    int getStatus();

    Map<String, List<String>> getHeaders();
}
