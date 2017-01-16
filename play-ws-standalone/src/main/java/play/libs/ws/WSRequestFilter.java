/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

/**
 * Request Filter.
 */
public interface WSRequestFilter {
    WSRequestExecutor apply(WSRequestExecutor executor);
}
