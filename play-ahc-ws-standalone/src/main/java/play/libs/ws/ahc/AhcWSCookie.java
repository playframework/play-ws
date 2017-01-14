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
    public Object getUnderlying() {
        return ahcCookie;
    }

    public String getDomain() {
        return ahcCookie.getDomain();
    }

    public String getName() {
        return ahcCookie.getName();
    }

    public String getValue() {
        return ahcCookie.getValue();
    }

    public String getPath() {
        return ahcCookie.getPath();
    }

    public long getMaxAge() {
        return ahcCookie.getMaxAge();
    }

    public boolean isSecure() {
        return ahcCookie.isSecure();
    }
}
