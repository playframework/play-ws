/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws;

import java.util.Optional;

/**
 * A WS Cookie.
 */
public interface WSCookie {

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
    Optional<String> getPath();

    /**
     * @return the cookie domain.
     */
    Optional<String> getDomain();

    /**
     * @return the cookie max age, in seconds.
     */
    Optional<Long> getMaxAge();

    /**
     * @return if the cookie is secure or not.
     */
    boolean isSecure();

    /**
     * @return if the cookie is accessed only server side.
     */
    boolean isHttpOnly();

    // Cookie ports should not be used; cookies for a given host are shared across
    // all the ports on that host.
}
