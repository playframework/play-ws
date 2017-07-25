/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws.ahc;

import play.libs.ws.*;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.proxy.ProxyServer;
import play.shaded.ahc.org.asynchttpclient.util.HttpUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public class AhcCurlRequestLogger implements WSRequestFilter {

    private final org.slf4j.Logger logger;

    public AhcCurlRequestLogger(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    public AhcCurlRequestLogger() {
        this(org.slf4j.LoggerFactory.getLogger("play.libs.ws.ahc.AhcCurlRequestLogger"));
    }

    @Override
    public WSRequestExecutor apply(WSRequestExecutor requestExecutor) {
        return request -> {
            logger.info(toCurl((StandaloneAhcWSRequest)request));
            return requestExecutor.apply(request);
        };
    }

    private String toCurl(StandaloneAhcWSRequest request) {
        final StringBuilder b = new StringBuilder("curl \\\n");

        // verbose, since it's a fair bet this is for debugging
        b.append("  --verbose")
         .append(" \\\n");

        // method
        b.append("  --request ").append(request.getMethod())
         .append(" \\\n");

        //authentication
        request.getAuth().ifPresent(auth -> {
            String encodedPasswd = Base64.getUrlEncoder().encodeToString((auth.getUsername() + ":" + auth.getPassword()).getBytes(StandardCharsets.US_ASCII));
            b.append("  --header \"Authorization: Basic ").append(quote(encodedPasswd))
             .append(" \\\n");
        });

        // headers
        request.getHeaders().forEach((name, values) ->
                values.forEach(v ->
                    b.append("  --header '").append(quote(name)).append(": ").append(quote(v))
                     .append(" \\\n")
                )
        );

        // body
        request.getBody().ifPresent(requestBody -> {
            if (requestBody instanceof InMemoryBodyWritable) {
                InMemoryBodyWritable inMemoryBody = (InMemoryBodyWritable) requestBody;

                String charset = findCharset(request);
                String bodyString = inMemoryBody.body().get().decodeString(charset);

                b.append("  --data '").append(quote(bodyString)).append("'")
                 .append(" \\\n");
            } else {
                throw new UnsupportedOperationException("Unsupported body type " + requestBody.getClass());
            }
        });

        // pull out some underlying values from the request.  This creates a new Request
        // but should be harmless.
        Request ahcRequest = request.buildRequest();
        ProxyServer proxyServer = ahcRequest.getProxyServer();
        if (proxyServer != null) {
            b.append("  --proxy ").append(proxyServer.getHost()).append(":").append(proxyServer.getPort())
             .append(" \\\n");
        }

        // url
        b.append("  '").append(quote(ahcRequest.getUrl())).append("'");
        return b.toString();
    }

    private String findCharset(StandaloneAhcWSRequest request) {
        return Optional.ofNullable(request.getContentType())
                .map(contentType ->
                    Optional.ofNullable(HttpUtils.parseCharset(contentType))
                            .orElse(StandardCharsets.UTF_8).name()
                ).orElse(StandardCharsets.UTF_8.name());
    }

    private String quote(String unsafe) {
        return unsafe.replace("'", "'\\''");
    }

}
