/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.nio.file.StandardCopyOption;

/**
 * A trusted test server with both matching- and mismatching-host request URLs. It can require a client
 * certificate, and its responses name the client certificate it received.
 */
final class WrongHostTlsTestServer implements AutoCloseable {
    private static final char[] PASSWORD = "changeit".toCharArray();

    private final Path trustStorePath;
    private final Path clientKeyStorePath;
    private final HttpsServer server;

    WrongHostTlsTestServer() throws Exception {
        this(false);
    }

    WrongHostTlsTestServer(boolean requireClientCertificate) throws Exception {
        KeyStore serverStore = load("/hostname-test-server.p12");
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(serverStore, PASSWORD);

        KeyStore clientStore = load("/hostname-test-client.p12");
        KeyStore trustedClients = KeyStore.getInstance("PKCS12");
        trustedClients.load(null, null);
        trustedClients.setCertificateEntry("client", clientStore.getCertificate("client"));
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustedClients);

        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);

        trustStorePath = copy("/hostname-test-trust.p12", "play-ws-wrong-host-trust-");
        clientKeyStorePath = copy("/hostname-test-client.p12", "play-ws-wrong-host-client-");

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters parameters = getSSLContext().getDefaultSSLParameters();
                parameters.setNeedClientAuth(requireClientCertificate);
                params.setSSLParameters(parameters);
            }
        });
        server.createContext("/", exchange -> {
            String client;
            try {
                X509Certificate certificate =
                    (X509Certificate) ((HttpsExchange) exchange).getSSLSession().getPeerCertificates()[0];
                client = certificate.getSubjectX500Principal().getName();
            } catch (SSLPeerUnverifiedException e) {
                client = "none";
            }
            byte[] body = ("client=" + client).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    private static KeyStore load(String resource) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = WrongHostTlsTestServer.class.getResourceAsStream(resource)) {
            store.load(input, PASSWORD);
        }
        return store;
    }

    private static Path copy(String resource, String prefix) throws Exception {
        Path path = Files.createTempFile(prefix, ".p12");
        try (InputStream input = WrongHostTlsTestServer.class.getResourceAsStream(resource)) {
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return path;
    }

    String matchingUrl() {
        return "https://localhost:" + server.getAddress().getPort() + "/";
    }

    String wrongHostUrl() {
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    String trustStorePath() {
        return trustStorePath.toString();
    }

    String clientKeyStorePath() {
        return clientKeyStorePath.toString();
    }

    @Override
    public void close() throws Exception {
        server.stop(0);
        Files.deleteIfExists(trustStorePath);
        Files.deleteIfExists(clientKeyStorePath);
    }
}
