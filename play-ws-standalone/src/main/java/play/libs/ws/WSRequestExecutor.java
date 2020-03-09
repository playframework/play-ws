/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;


public interface WSRequestExecutor extends Function<StandaloneWSRequest, CompletionStage<StandaloneWSResponse>> {

}
