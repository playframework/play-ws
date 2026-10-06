/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.oauth;

import play.shaded.ahc.org.asynchttpclient.Param;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase;
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator;
import play.shaded.oauth.oauth.signpost.basic.DefaultOAuthConsumer;
import play.shaded.oauth.oauth.signpost.exception.OAuthException;
import play.shaded.oauth.oauth.signpost.http.HttpRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Internal Signpost adapter that keeps Play WS OAuth 1 signing independent of AHC's removed OAuth module.
 */
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
        DefaultOAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);
        consumer.setTokenWithSecret(token, tokenSecret);
        try {
            consumer.sign(new AhcRequestAdapter(request, requestBuilder));
        } catch (OAuthException exception) {
            throw new IllegalArgumentException("Could not sign the request with OAuth 1", exception);
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
