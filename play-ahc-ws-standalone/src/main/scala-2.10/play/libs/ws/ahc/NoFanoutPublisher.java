package play.libs.ws.ahc;

import akka.stream.javadsl.Sink;
import org.reactivestreams.Publisher;

import java.nio.ByteBuffer;

/**
 * A 2.10 version of a WITHOUT_FANOUT publisher
 */
public class NoFanoutPublisher {
    public static final Sink<ByteBuffer, Publisher<ByteBuffer>> sink = Sink.asPublisher(false);
}
