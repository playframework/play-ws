/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.WSCookie;

/**
 * The AHC implementation of a WS cookie.
 */
public class AhcWSCookie implements WSCookie {

    private final play.shaded.ahc.org.asynchttpclient.cookie.Cookie ahcCookie;

    public AhcWSCookie(play.shaded.ahc.org.asynchttpclient.cookie.Cookie ahcCookie) {
        this.ahcCookie = ahcCookie;
    }

    /**
     * Returns the underlying "native" object for the cookie.
     */
    @Override
    public play.shaded.ahc.org.asynchttpclient.cookie.Cookie getUnderlying() {
        return ahcCookie;
    }

    @Override
    public String getDomain() {
        return ahcCookie.getDomain();
    }

    @Override
    public String getName() {
        return ahcCookie.getName();
    }

    @Override
    public String getValue() {
        return ahcCookie.getValue();
    }

    @Override
    public String getPath() {
        return ahcCookie.getPath();
    }

    @Override
    public long getMaxAge() {
        return ahcCookie.getMaxAge();
    }

    @Override
    public boolean isSecure() {
        return ahcCookie.isSecure();
    }

    @Override
    public boolean httpOnly() {
        return ahcCookie.isHttpOnly();
    }
}
