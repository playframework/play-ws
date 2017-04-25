/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

/**
 * A WS Cookie.
 */
public interface WSCookie {

    /**
     * Returns the underlying "native" object for the cookie.
     * <p>
     * This is probably an <code>org.asynchttpclient.cookie.Cookie</code>.
     *
     * @return the "native" object
     */
    Object getUnderlying();

    /**
     * @return the cookie domain.
     */
    String getDomain();

    /**
     * @return the cookie name.
     */
    String getName();

    /**
     * @return the cookie value.
     */
    String getValue();

    /**
     * @return the cookie path.
     */
    String getPath();

    /**
     * @return the cookie max age, in seconds.
     */
    long getMaxAge();

    /**
     * @return if the cookie is secure or not.
     */
    boolean isSecure();

    /**
     * @return if the cookie is accessed only server side.
     */
    boolean httpOnly();

    // Cookie ports should not be used; cookies for a given host are shared across
    // all the ports on that host.
}
