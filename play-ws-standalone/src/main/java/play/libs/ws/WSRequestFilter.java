/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.function.Function;

/**
 * Request Filter.  Use this to add your own filters onto a request at execution time.
 *
 * <pre>
 * {@code
 * public class HeaderAppendingFilter implements WSRequestFilter {
 *     private final String key;
 *     private final String value;
 *
 *     public HeaderAppendingFilter(String key, String value) {
 *         this.key = key;
 *         this.value = value;
 *     }
 *
 *     @Override
 *     public WSRequestExecutor apply(WSRequestExecutor executor) {
 *         return request -> executor.apply(request.setHeader(key, value));
 *     }
 * }
 * }</pre>
 */
public interface WSRequestFilter extends Function<WSRequestExecutor, WSRequestExecutor> {

}
