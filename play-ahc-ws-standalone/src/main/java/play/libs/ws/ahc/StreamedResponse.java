/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.reactivestreams.Publisher;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import scala.collection.Seq;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import scala.compat.java8.ScalaStreamSupport;

import static java.util.stream.Collectors.*;
import static scala.collection.JavaConverters.seqAsJavaListConverter;

public class StreamedResponse implements StandaloneWSResponse, CookieBuilder {

    private final int status;
    private final Map<String, List<String>> headers;
    private final String statusText;
    private final URI uri;
    private final Publisher<HttpResponseBodyPart> publisher;
    private final StandaloneAhcWSClient client;

    private List<WSCookie> cookies;

    public StreamedResponse(StandaloneAhcWSClient client,
                            int status,
                            String statusText, URI uri,
                            scala.collection.Map<String, Seq<String>> headers,
                            Publisher<HttpResponseBodyPart> publisher) {
        this.client = client;
        this.status = status;
        this.statusText = statusText;
        this.uri = uri;
        this.headers = asJava(headers);
        this.publisher = publisher;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getStatusText() {
        return statusText;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    @Override
    public Object getUnderlying() {
        return publisher;
    }

    @Override
    public List<WSCookie> getCookies() {
        if (cookies == null) {
            cookies = buildCookies(headers);
        }
        return cookies;
    }

    @Override
    public WSCookie getCookie(String name) {
        Predicate<WSCookie> predicate = (WSCookie c) -> c.getName().equals(name);
        return getCookies().stream().filter(predicate).findFirst().orElse(null);
    }

    @Override
    public String getContentType() {
        return getSingleHeader(HttpHeaders.Names.CONTENT_TYPE).orElse("application/octet-stream");
    }

    @Override
    public <T> T getBody(BodyReadable<T> readable) {
        return readable.apply(this);
    }

    @Override
    public String getBody() {
        return getBodyAsBytes().utf8String();
    }

    @Override
    public ByteString getBodyAsBytes() {
        return client.blockingToByteString(getBodyAsSource());
    }

    @Override
    public Source<ByteString, ?> getBodyAsSource() {
        return Source.fromPublisher(publisher).map(bodyPart -> ByteString.fromArray(bodyPart.getBodyPartBytes()));
    }

    private java.util.Map<String, List<String>> asJava(scala.collection.Map<String, Seq<String>> scalaMap) {
        return ScalaStreamSupport.stream(scalaMap).collect(toMap(f -> f._1(), f -> seqAsJavaListConverter(f._2()).asJava()));
    }

}
