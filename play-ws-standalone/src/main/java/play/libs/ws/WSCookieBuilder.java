/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

public class WSCookieBuilder {
    private String name;
    private String value;
    private String domain;
    private String path;
    private Long maxAge;
    private boolean secure;
    private boolean httpOnly;

    public WSCookieBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public WSCookieBuilder setValue(String value) {
        this.value = value;
        return this;
    }

    public WSCookieBuilder setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public WSCookieBuilder setPath(String path) {
        this.path = path;
        return this;
    }

    public WSCookieBuilder setMaxAge(Long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public WSCookieBuilder setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public WSCookieBuilder setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public WSCookie build() {
        return new DefaultWSCookie(name, value, domain, path, maxAge, secure, httpOnly);
    }
}