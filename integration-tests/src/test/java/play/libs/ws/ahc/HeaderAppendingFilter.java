/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.*;

public class HeaderAppendingFilter implements WSRequestFilter {

    private final String key;
    private final String value;

    public HeaderAppendingFilter(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public WSRequestExecutor apply(WSRequestExecutor executor) {
        return request -> executor.apply(request.addHeader(key, value));
    }
}
