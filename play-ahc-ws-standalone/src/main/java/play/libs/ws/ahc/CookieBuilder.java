/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.WSCookie;
import play.libs.ws.WSCookieBuilder;
import play.shaded.ahc.io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import play.shaded.ahc.io.netty.handler.codec.http.cookie.Cookie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE2;
import static play.shaded.ahc.org.asynchttpclient.util.MiscUtils.isNonEmpty;

interface CookieBuilder {

    default List<WSCookie> buildCookies(Map<String, List<String>> headers) {
        List<String> setCookieHeaders = headers.get(SET_COOKIE2);

        if (!isNonEmpty(setCookieHeaders)) {
            setCookieHeaders = headers.get(SET_COOKIE);
        }

        if (isNonEmpty(setCookieHeaders)) {
            List<WSCookie> cookies = new ArrayList<>(setCookieHeaders.size());
            for (String value : setCookieHeaders) {
                Cookie c = isUseLaxCookieEncoder() ? ClientCookieDecoder.LAX.decode(value) : ClientCookieDecoder.STRICT.decode(value);
                if (c != null) {
                    WSCookie wsCookie = new WSCookieBuilder()
                            .setName(c.name())
                            .setValue(c.value())
                            .setDomain(c.domain())
                            .setPath(c.path())
                            .setMaxAge(c.maxAge())
                            .setSecure(c.isSecure())
                            .setHttpOnly(c.isHttpOnly())
                            .build();
                    cookies.add(wsCookie);
                }
            }
            return Collections.unmodifiableList(cookies);
        }

        return Collections.emptyList();
    }

    boolean isUseLaxCookieEncoder();
}
