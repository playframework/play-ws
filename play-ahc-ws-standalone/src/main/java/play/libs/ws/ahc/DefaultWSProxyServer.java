/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.libs.ws.WSProxyServer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultWSProxyServer implements WSProxyServer {
    private final String host;
    private final int port;
    private final String protocol;
    private final String proxyType;
    private final String principal;
    private final String password;
    private final String ntlmDomain;
    private final List<String> nonProxyHosts;
    private final String encoding;

    DefaultWSProxyServer(String host,
                         Integer port,
                         String protocol,
                         String proxyType,
                         String principal,
                         String password,
                         String ntlmDomain,
                         List<String> nonProxyHosts,
                         String encoding) {
        this.host = Objects.requireNonNull(host, "host cannot be null!");
        this.port = Objects.requireNonNull(port, "port cannot be null");
        this.protocol = protocol;
        this.proxyType = proxyType;
        this.principal = principal;
        this.password = password;
        this.ntlmDomain = ntlmDomain;
        this.nonProxyHosts = nonProxyHosts;
        this.encoding = encoding;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public Optional<String> getProtocol() {
        return Optional.ofNullable(this.protocol);
    }

    @Override
    public Optional<String> getProxyType() {
        return Optional.ofNullable(this.proxyType);
    }

    @Override
    public Optional<String> getPrincipal() {
        return Optional.ofNullable(this.principal);
    }

    @Override
    public Optional<String> getPassword() {
        return Optional.ofNullable(this.password);
    }

    @Override
    public Optional<String> getNtlmDomain() {
        return Optional.ofNullable(this.ntlmDomain);
    }

    @Override
    public Optional<String> getEncoding() {
        return Optional.ofNullable(this.encoding);
    }

    @Override
    public Optional<List<String>> getNonProxyHosts() {
        return Optional.ofNullable(this.nonProxyHosts);
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String host;
        private Integer port;
        private String protocol;
        private String proxyType;
        private String principal;
        private String password;
        private String ntlmDomain;
        private List<String> nonProxyHosts;
        private String encoding;

        public Builder withHost(String host) {
            this.host = host;
            return this;
        }

        public Builder withPort(int port) {
            this.port = port;
            return this;
        }

        public Builder withProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder withProxyType(String proxyType) {
            this.proxyType = proxyType;
            return this;
        }

        public Builder withPrincipal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withNtlmDomain(String ntlmDomain) {
            this.ntlmDomain = ntlmDomain;
            return this;
        }

        public Builder withNonProxyHosts(List<String> nonProxyHosts) {
            this.nonProxyHosts = nonProxyHosts;
            return this;
        }

        public Builder withEncoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public WSProxyServer build() {
            return new DefaultWSProxyServer(host,
                    port,
                    protocol,
                    proxyType,
                    principal,
                    password,
                    ntlmDomain,
                    nonProxyHosts,
                    encoding);
        }
    }
}
