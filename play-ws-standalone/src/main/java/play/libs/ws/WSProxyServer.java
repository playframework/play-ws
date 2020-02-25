/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

import java.util.List;
import java.util.Optional;

public interface WSProxyServer {
    /** The hostname of the proxy server. */
    String getHost();

    /** The port of the proxy server. */
    int getPort();

    /** The protocol of the proxy server: "kerberos", "ntlm", "https" etc.  Defaults to "http" if not specified. */
    Optional<String> getProtocol();

    /** The proxy type, "http", "socksv4", or "socksv5".  Defaults to "http" if not specified. */
    Optional<String> getProxyType();

    /** The principal (aka username) of the credentials for the proxy server. */
    Optional<String> getPrincipal();

    /** The password for the credentials for the proxy server. */
    Optional<String> getPassword();

    Optional<String> getNtlmDomain();

    /** The realm's charset. */
    Optional<String> getEncoding();

    Optional<List<String>> getNonProxyHosts();
}
