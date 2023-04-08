/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.function.Function;

/**
 * Converts a response body into type R
 */
public interface BodyReadable<R> extends Function<StandaloneWSResponse, R> {

}
