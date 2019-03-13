/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.oauth;

import play.shaded.oauth.oauth.signpost.OAuthConsumer;
import play.shaded.oauth.oauth.signpost.OAuthProvider;
import play.shaded.oauth.oauth.signpost.basic.DefaultOAuthConsumer;
import play.shaded.oauth.oauth.signpost.basic.DefaultOAuthProvider;
import play.shaded.oauth.oauth.signpost.exception.OAuthException;
import play.shaded.ahc.org.asynchttpclient.oauth.OAuthSignatureCalculator;
import play.libs.ws.WSSignatureCalculator;

public class OAuth {

    private ServiceInfo info;
    private OAuthProvider provider;

    public OAuth(ServiceInfo info) {
        this(info, true);
    }

    public OAuth(ServiceInfo info, boolean use10a) {
        this.info = info;
        this.provider = new DefaultOAuthProvider(info.requestTokenURL, info.accessTokenURL, info.authorizationURL);
        this.provider.setOAuth10a(use10a);
    }

    public ServiceInfo getInfo() {
        return info;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    /**
     * Request the request token and secret.
     *
     * @param callbackURL the URL where the provider should redirect to (usually a URL on the current app)
     * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
     */
    public RequestToken retrieveRequestToken(String callbackURL) {
        OAuthConsumer consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret);
        try {
            provider.retrieveRequestToken(consumer, callbackURL);
            return new RequestToken(consumer.getToken(), consumer.getTokenSecret());
        } catch (OAuthException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Exchange a request token for an access token.
     *
     * @param token    the token/secret pair obtained from a previous call
     * @param verifier a string you got through your user, with redirection
     * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
     */
    public RequestToken retrieveAccessToken(RequestToken token, String verifier) {
        OAuthConsumer consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret);
        consumer.setTokenWithSecret(token.token, token.secret);
        try {
            provider.retrieveAccessToken(consumer, verifier);
            return new RequestToken(consumer.getToken(), consumer.getTokenSecret());
        } catch (OAuthException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * The URL where the user needs to be redirected to grant authorization to your application.
     *
     * @param token request token
     * @return the url
     */
    public String redirectUrl(String token) {
        return play.shaded.oauth.oauth.signpost.OAuth.addQueryParameters(
                provider.getAuthorizationWebsiteUrl(),
                play.shaded.oauth.oauth.signpost.OAuth.OAUTH_TOKEN,
                token
        );
    }

    /**
     * A consumer key / consumer secret pair that the OAuth provider gave you, to identify your application.
     */
    public static class ConsumerKey {
        public String key;
        public String secret;

        public ConsumerKey(String key, String secret) {
            this.key = key;
            this.secret = secret;
        }
    }

    /**
     * A request token / token secret pair, to be used for a specific user.
     */
    public static class RequestToken {
        public String token;
        public String secret;

        public RequestToken(String token, String secret) {
            this.token = token;
            this.secret = secret;
        }
    }

    /**
     * The information identifying a oauth provider: URLs and the consumer key / consumer secret pair.
     */
    public static class ServiceInfo {
        public String requestTokenURL;
        public String accessTokenURL;
        public String authorizationURL;
        public ConsumerKey key;

        public ServiceInfo(String requestTokenURL, String accessTokenURL, String authorizationURL, ConsumerKey key) {
            this.requestTokenURL = requestTokenURL;
            this.accessTokenURL = accessTokenURL;
            this.authorizationURL = authorizationURL;
            this.key = key;
        }
    }

    /**
     * A signature calculator for the Play WS API.
     * <p>
     * Example:
     * {{{
     * WS.url("http://example.com/protected").sign(OAuthCalculator(service, token)).get()
     * }}}
     */
    public static class OAuthCalculator implements WSSignatureCalculator {

        private OAuthSignatureCalculator calculator;

        public OAuthCalculator(ConsumerKey consumerKey, RequestToken token) {
            play.shaded.ahc.org.asynchttpclient.oauth.ConsumerKey ahcConsumerKey = new play.shaded.ahc.org.asynchttpclient.oauth.ConsumerKey(consumerKey.key, consumerKey.secret);
            play.shaded.ahc.org.asynchttpclient.oauth.RequestToken ahcRequestToken = new play.shaded.ahc.org.asynchttpclient.oauth.RequestToken(token.token, token.secret);
            calculator = new OAuthSignatureCalculator(ahcConsumerKey, ahcRequestToken);
        }

        public OAuthSignatureCalculator getCalculator() {
            return calculator;
        }
    }

}
