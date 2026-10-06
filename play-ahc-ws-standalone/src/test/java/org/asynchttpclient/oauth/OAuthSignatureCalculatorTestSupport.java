/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.asynchttpclient.oauth;

import org.asynchttpclient.Request;

import java.security.GeneralSecurityException;

/** Test access to AHC 2's package-private deterministic OAuth signing overload. */
public final class OAuthSignatureCalculatorTestSupport {

    private OAuthSignatureCalculatorTestSupport() {}

    public static String authorizationHeader(
            Request request,
            String consumerKey,
            String consumerSecret,
            String token,
            String tokenSecret,
            long timestamp,
            String nonce)
            throws GeneralSecurityException {
        return new OAuthSignatureCalculatorInstance()
                .computeAuthorizationHeader(
                        new ConsumerKey(consumerKey, consumerSecret),
                        new RequestToken(token, tokenSecret),
                        request.getUri(),
                        request.getMethod(),
                        request.getFormParams(),
                        request.getQueryParams(),
                        timestamp,
                        nonce);
    }
}
