/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.*;

import java.util.List;

public class CallbackRequestFilter implements WSRequestFilter {

    private final List<Integer> callList;
    private final Integer value;

    public CallbackRequestFilter(List<Integer> callList, Integer value) {
        this.callList = callList;
        this.value = value;
    }

    @Override
    public WSRequestExecutor apply(WSRequestExecutor executor) {
        callList.add(value);
        return executor;
    }
}


