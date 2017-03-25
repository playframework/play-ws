package play.libs.ws.ahc;

import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import org.reactivestreams.Publisher;

import java.nio.ByteBuffer;

/**
 * A 2.12 version of a WITHOUT_FANOUT publisher
 */
public class NoFanoutPublisher {
   public static final Sink<ByteBuffer, Publisher<ByteBuffer>> sink = Sink.asPublisher(AsPublisher.WITHOUT_FANOUT);
}
