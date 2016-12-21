/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.concurrent.CompletionStage;

/**
 * A streamed response containing a response header and a streamable body.
 */
public interface StreamedResponse {

    public WSResponseHeaders getHeaders();

    public Source<ByteString, ?> getBody();

    public static CompletionStage<StreamedResponse> from(Future<play.api.libs.ws.StreamedResponse> from) {
        CompletionStage<play.api.libs.ws.StreamedResponse> res = FutureConverters.toJava(from);
        java.util.function.Function<play.api.libs.ws.StreamedResponse, StreamedResponse> mapper = response ->
                play.api.libs.ws.StreamedResponse.apply(response.headers(), response.body());
        return res.thenApply(mapper);
    }

}
