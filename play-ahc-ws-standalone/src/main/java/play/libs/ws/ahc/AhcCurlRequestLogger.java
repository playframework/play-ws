/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.InMemoryBodyWritable;
import play.libs.ws.SourceBodyWritable;
import play.libs.ws.StandaloneWSRequest;
import play.libs.ws.WSRequestExecutor;
import play.libs.ws.WSRequestFilter;
import play.shaded.ahc.org.asynchttpclient.Realm;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyServer;
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logs {@link StandaloneWSRequest} and pulls information into Curl format to an SLF4J logger.
 *
 * <p><b>Warning:</b> the command is logged at INFO, without redaction. It can contain passwords,
 * {@code Authorization} and {@code Proxy-Authorization} headers, session cookies, credentials and other secrets in
 * the URL, and in-memory request bodies. Use it only for controlled debugging, and do not enable it for traffic that
 * carries secrets.
 *
 * @see <a href="https://curl.haxx.se/">https://curl.haxx.se/</a>
 */
public class AhcCurlRequestLogger implements WSRequestFilter {

    private final org.slf4j.Logger logger;

    public AhcCurlRequestLogger(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    public AhcCurlRequestLogger() {
        this(org.slf4j.LoggerFactory.getLogger(AhcCurlRequestLogger.class));
    }

    private static final Pattern SINGLE_QUOTE_REPLACE = Pattern.compile("'", Pattern.LITERAL);

    @Override
    public WSRequestExecutor apply(WSRequestExecutor requestExecutor) {
        return request -> {
            logger.info(toCurl((StandaloneAhcWSRequest)request));
            return requestExecutor.apply(request);
        };
    }

    private String toCurl(StandaloneAhcWSRequest request) {
        // pull out some underlying values from the request. This creates a new Request, without a streamed body, so
        // that the stream is left to the request itself.
        boolean streamed = request.getBody().filter(body -> body instanceof SourceBodyWritable).isPresent();
        Request ahcRequest = streamed ? request.buildRequestWithoutBody() : request.buildRequest();

        final StringBuilder b = new StringBuilder("curl \\\n");

        // verbose, since it's a fair bet this is for debugging
        b.append("  --verbose")
         .append(" \\\n");

        // method
        b.append("  --request ").append(request.getMethod())
         .append(" \\\n");

        // authentication: the header AHC sends for Basic. Other schemes don't send the password as it is.
        Realm realm = ahcRequest.getRealm();
        if (realm != null && realm.getScheme() == Realm.AuthScheme.BASIC) {
            b.append("  --header 'Authorization: ").append(quote(realm.getBasicAuthHeader())).append('\'')
             .append(" \\\n");
        }

        // headers, without the length of a body that the command leaves out, so that the server doesn't wait for it
        request.getHeaders().forEach((name, values) -> {
            boolean bodyLength = name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("Transfer-Encoding");
            if (!streamed || !bodyLength) {
                values.forEach(v ->
                    b.append("  --header '").append(quote(name)).append(": ").append(quote(v)).append('\'')
                     .append(" \\\n")
                );
            }
        });

        // cookies
        request.getCookies().forEach(cookie ->
                b.append("  --cookie '").append(quote(cookie.getName())).append('=').append(quote(cookie.getValue()))
                 .append('\'').append(" \\\n")
        );

        // body
        request.getBody().ifPresent(requestBody -> {
            if (requestBody instanceof InMemoryBodyWritable) {
                InMemoryBodyWritable inMemoryBody = (InMemoryBodyWritable) requestBody;

                String charset = findCharset(request);
                String bodyString = inMemoryBody.body().get().decodeString(charset);

                b.append("  --data '").append(quote(bodyString)).append('\'')
                 .append(" \\\n");
            }
            // A streamed body can be read only once, so the request reads it, not this.
        });

        ProxyServer proxyServer = ahcRequest.getProxyServer();
        if (proxyServer != null) {
            b.append("  --proxy ").append(proxyServer.getHost()).append(':').append(proxyServer.getPort())
             .append(" \\\n");
        }

        // url
        b.append("  '").append(quote(ahcRequest.getUrl())).append('\'');

        if (streamed) {
            b.append("\n# The streamed request body is not shown.");
        }
        return b.toString();
    }

    private static String findCharset(StandaloneAhcWSRequest request) {
        return Optional.ofNullable(request.getContentType())
                .flatMap(contentType -> contentType.map(HttpUtils::extractContentTypeCharsetAttribute))
                .orElse(StandardCharsets.UTF_8).name();
    }

    private static String quote(String unsafe) {
        return SINGLE_QUOTE_REPLACE.matcher(
                unsafe
        ).replaceAll(Matcher.quoteReplacement("'\\''"));
    }

}
