/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

/**
 * Holds information for request authentication.
 *
 * @see WSAuthScheme
 * @see StandaloneWSRequest#setAuth(String)
 * @see <a href="https://tools.ietf.org/html/rfc7235">RFC 7235 - Hypertext Transfer Protocol (HTTP/1.1): Authentication</a>
 */
public class WSAuthInfo {

    private final String username;
    private final String password;
    private final WSAuthScheme scheme;

    public WSAuthInfo(String username, String password, WSAuthScheme scheme) {
        this.username = username;
        this.password = password;
        this.scheme = scheme;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public WSAuthScheme getScheme() {
        return scheme;
    }
}
