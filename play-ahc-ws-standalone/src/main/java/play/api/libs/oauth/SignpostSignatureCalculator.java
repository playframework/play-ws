/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.oauth;

import org.apache.pekko.annotation.InternalApi;
import play.shaded.ahc.org.asynchttpclient.Param;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase;
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator;
import play.shaded.oauth.oauth.signpost.OAuth;
import play.shaded.oauth.oauth.signpost.basic.DefaultOAuthConsumer;
import play.shaded.oauth.oauth.signpost.exception.OAuthException;
import play.shaded.oauth.oauth.signpost.http.HttpParameters;
import play.shaded.oauth.oauth.signpost.http.HttpRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Internal Signpost adapter that keeps Play WS OAuth 1 signing independent of AHC's removed OAuth module.
 */
@InternalApi
public final class SignpostSignatureCalculator implements SignatureCalculator {

    private final String consumerKey;
    private final String consumerSecret;
    private final String token;
    private final String tokenSecret;

    public SignpostSignatureCalculator(String consumerKey, String consumerSecret, String token, String tokenSecret) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.token = token;
        this.tokenSecret = tokenSecret;
    }

    @Override
    public void calculateAndAddSignature(Request request, RequestBuilderBase<?> requestBuilder) {
        DefaultOAuthConsumer consumer = new MergingOAuthConsumer(consumerKey, consumerSecret);
        consumer.setTokenWithSecret(token, tokenSecret);
        try {
            consumer.sign(new AhcRequestAdapter(request, requestBuilder));
        } catch (OAuthException exception) {
            throw new IllegalArgumentException("Could not sign the request with OAuth 1", exception);
        }
    }

    /**
     * Signpost's bulk parameter merge replaces values with the same name from
     * an earlier source. OAuth requires all query and form values to be
     * included, including when a name occurs in both sources.
     */
    private static final class MergingOAuthConsumer extends DefaultOAuthConsumer {

        private MergingOAuthConsumer(String consumerKey, String consumerSecret) {
            super(consumerKey, consumerSecret);
        }

        @Override
        protected void collectQueryParameters(HttpRequest request, HttpParameters parameters) {
            String url = request.getRequestUrl();
            int queryStart = url.indexOf('?');
            if (queryStart >= 0) {
                merge(parameters, OAuth.decodeForm(url.substring(queryStart + 1)));
            }
        }

        @Override
        protected void collectBodyParameters(HttpRequest request, HttpParameters parameters) throws IOException {
            String contentType = request.getContentType();
            if (contentType != null && contentType.startsWith(OAuth.FORM_ENCODED)) {
                merge(parameters, OAuth.decodeForm(request.getMessagePayload()));
            }
        }

        private static void merge(HttpParameters target, HttpParameters source) {
            for (Map.Entry<String, SortedSet<String>> parameter : source.entrySet()) {
                if (parameter.getValue().isEmpty()) {
                    // Signpost represents a bare parameter such as "flag" with an empty value set. OAuth normalizes it
                    // like "flag=", so retain it as an empty value instead of dropping the parameter.
                    target.put(parameter.getKey(), "", true);
                } else {
                    for (String value : parameter.getValue()) {
                        target.put(parameter.getKey(), value, true);
                    }
                }
            }
        }
    }

    private static final class AhcRequestAdapter implements HttpRequest {

        private final Request request;
        private final RequestBuilderBase<?> requestBuilder;

        private AhcRequestAdapter(Request request, RequestBuilderBase<?> requestBuilder) {
            this.request = request;
            this.requestBuilder = requestBuilder;
        }

        @Override
        public String getMethod() {
            return request.getMethod();
        }

        @Override
        public String getRequestUrl() {
            return request.getUrl();
        }

        @Override
        public void setRequestUrl(String url) {
            requestBuilder.setUrl(url);
        }

        @Override
        public void setHeader(String name, String value) {
            requestBuilder.setHeader(name, value);
        }

        @Override
        public String getHeader(String name) {
            return request.getHeaders().get(name);
        }

        @Override
        public Map<String, String> getAllHeaders() {
            // Signpost's HttpRequest SPI cannot represent duplicate header values. Its signing path only needs
            // single-valued OAuth-relevant headers, so retain the first AHC value for each name.
            Map<String, String> headers = new LinkedHashMap<>();
            request.getHeaders().names().forEach(name -> headers.put(name, request.getHeaders().get(name)));
            return headers;
        }

        @Override
        public InputStream getMessagePayload() {
            String form = request.getFormParams().stream()
                    .map(AhcRequestAdapter::encode)
                    .collect(Collectors.joining("&"));
            return new ByteArrayInputStream(form.getBytes(StandardCharsets.UTF_8));
        }

        private static String encode(Param param) {
            return URLEncoder.encode(param.getName(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8);
        }

        @Override
        public String getContentType() {
            return request.getHeaders().get("Content-Type");
        }

        @Override
        public Object unwrap() {
            return request;
        }
    }
}
