/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import java.util.concurrent.CompletionStage;

public interface WSRequestExecutor {
   CompletionStage<? extends StandaloneWSResponse> apply(StandaloneWSRequest request);
}
