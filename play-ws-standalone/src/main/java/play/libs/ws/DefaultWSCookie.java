/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.Optional;

/**
 * The implementation of a WS cookie.
 */
public class DefaultWSCookie implements WSCookie {
    private String name;
    private String value;
    private String domain;
    private String path;
    private Long maxAge;
    private boolean secure = false;
    private boolean httpOnly = false;

    public DefaultWSCookie(String name, String value, String domain, String path, Long maxAge, boolean secure, boolean httpOnly) {
        this.name = name;
        this.value = value;
        this.domain = domain;
        this.path = path;
        this.maxAge = maxAge;
        this.secure = secure;
        this.httpOnly = httpOnly;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Optional<String> getDomain() {
        return Optional.ofNullable(domain);
    }

    @Override
    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    @Override
    public Optional<Long> getMaxAge() {
        if (maxAge != null && maxAge.longValue() > -1L) {
            return Optional.of(maxAge);
        } else {
            return Optional.ofNullable(maxAge);
        }
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

}
