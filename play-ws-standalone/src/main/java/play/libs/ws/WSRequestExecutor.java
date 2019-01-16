/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;


public interface WSRequestExecutor extends Function<StandaloneWSRequest, CompletionStage<StandaloneWSResponse>> {

}
