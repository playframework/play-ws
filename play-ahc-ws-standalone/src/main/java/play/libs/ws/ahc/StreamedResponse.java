/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.reactivestreams.Publisher;
import play.api.libs.ws.ahc.AhcWSUtils;
import play.libs.ws.BodyReadable;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.WSCookie;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import scala.collection.Seq;
import scala.jdk.javaapi.StreamConverters;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toMap;
import scala.jdk.javaapi.CollectionConverters;

public class StreamedResponse implements StandaloneWSResponse, CookieBuilder {

    private final int status;
    private final Map<String, List<String>> headers;
    private final String statusText;
    private final URI uri;
    private final Publisher<HttpResponseBodyPart> publisher;
    private final StandaloneAhcWSClient client;
    private final boolean useLaxCookieEncoder;

    private List<WSCookie> cookies;

    public StreamedResponse(StandaloneAhcWSClient client,
                            int status,
                            String statusText, URI uri,
                            scala.collection.Map<String, Seq<String>> headers,
                            Publisher<HttpResponseBodyPart> publisher,
                            boolean useLaxCookieEncoder) {
        this.client = client;
        this.status = status;
        this.statusText = statusText;
        this.uri = uri;
        this.headers = asJava(headers);
        this.publisher = publisher;
        this.useLaxCookieEncoder = useLaxCookieEncoder;
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
    public Optional<WSCookie> getCookie(String name) {
        Predicate<WSCookie> predicate = (WSCookie c) -> c.getName().equals(name);
        return getCookies().stream().filter(predicate).findFirst();
    }

    @Override
    public String getContentType() {
        return getSingleHeader(HttpHeaderNames.CONTENT_TYPE.toString()).orElse("application/octet-stream");
    }

    @Override
    public <T> T getBody(BodyReadable<T> readable) {
        return readable.apply(this);
    }

    @Override
    public String getBody() {
        return getBodyAsBytes().decodeString(AhcWSUtils.getCharset(getContentType()));
    }

    @Override
    public ByteString getBodyAsBytes() {
        return client.blockingToByteString(getBodyAsSource());
    }

    @Override
    public Source<ByteString, ?> getBodyAsSource() {
        return Source.fromPublisher(publisher).map(bodyPart -> ByteString.fromArray(bodyPart.getBodyPartBytes()));
    }

    public boolean isUseLaxCookieEncoder() {
        return useLaxCookieEncoder;
    }

    @Override
    public URI getUri() {
        return this.uri;
    }

    private static java.util.Map<String, List<String>> asJava(scala.collection.Map<String, Seq<String>> scalaMap) {
        return StreamConverters.asJavaSeqStream(scalaMap).collect(toMap(f -> f._1(), f -> CollectionConverters.asJava(f._2()),
                (l, r) -> {
                    final List<String> merged = new ArrayList<>(l.size() + r.size());
                    merged.addAll(l);
                    merged.addAll(r);
                    return merged;
                },
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
            )
        );
    }

}
