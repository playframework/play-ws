/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import play.shaded.ahc.io.netty.bootstrap.ServerBootstrap;
import play.shaded.ahc.io.netty.buffer.ByteBuf;
import play.shaded.ahc.io.netty.buffer.Unpooled;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.channel.ChannelFutureListener;
import play.shaded.ahc.io.netty.channel.ChannelHandlerContext;
import play.shaded.ahc.io.netty.channel.ChannelInboundHandlerAdapter;
import play.shaded.ahc.io.netty.channel.ChannelInitializer;
import play.shaded.ahc.io.netty.channel.MultiThreadIoEventLoopGroup;
import play.shaded.ahc.io.netty.channel.SimpleChannelInboundHandler;
import play.shaded.ahc.io.netty.channel.group.ChannelGroup;
import play.shaded.ahc.io.netty.channel.group.DefaultChannelGroup;
import play.shaded.ahc.io.netty.channel.nio.NioIoHandler;
import play.shaded.ahc.io.netty.channel.socket.nio.NioServerSocketChannel;
import play.shaded.ahc.io.netty.handler.codec.http.DefaultFullHttpResponse;
import play.shaded.ahc.io.netty.handler.codec.http.FullHttpRequest;
import play.shaded.ahc.io.netty.handler.codec.http.HttpObjectAggregator;
import play.shaded.ahc.io.netty.handler.codec.http.HttpServerCodec;
import play.shaded.ahc.io.netty.handler.codec.http.HttpUtil;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2Headers;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2DataFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2Headers;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2HeadersFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2MultiplexHandler;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2StreamChannel;
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolNames;
import play.shaded.ahc.io.netty.handler.ssl.SslHandler;
import play.shaded.ahc.io.netty.handler.ssl.SslHandshakeCompletionEvent;
import play.shaded.ahc.io.netty.handler.ssl.util.SelfSignedCertificate;
import play.shaded.ahc.io.netty.util.concurrent.GlobalEventExecutor;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpResponseStatus.OK;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * A TLS server for observing what the Play WS client negotiates.
 *
 * It selects h2 when the client offers it in ALPN (unless constructed as an HTTP/1.1-only server), and records the
 * last handshake: the protocols the client offered in ALPN, the TLS version, the cipher suite, and the client
 * certificate. It answers both HTTP/1.1 and h2 requests with the negotiated protocol as the body, or with a redirect.
 */
final class AlpnProtocolTestServer implements AutoCloseable {
    private static final char[] PASSWORD = "changeit".toCharArray();

    /**
     * What the server saw in the last successful handshake.
     *
     * @param offeredProtocols the client's ALPN offer, or null if it sent no ALPN extension
     * @param protocol the HTTP protocol the connection serves: "h2" or "http/1.1"
     * @param tlsVersion the negotiated TLS version
     * @param cipherSuite the negotiated cipher suite
     * @param clientCertificate the subject of the client certificate, or "none"
     */
    record Handshake(List<String> offeredProtocols, String protocol, String tlsVersion, String cipherSuite,
                     String clientCertificate) {
    }

    private final SelfSignedCertificate certificate;
    private final Path trustStorePath;
    private final Path clientKeyStorePath;
    private final MultiThreadIoEventLoopGroup group =
        new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final ChannelGroup clients =
        new DefaultChannelGroup("play-ws-alpn-protocol-test", GlobalEventExecutor.INSTANCE);
    private final AtomicReference<Handshake> lastHandshake = new AtomicReference<>();
    private final String redirectLocation;
    private final Channel serverChannel;

    AlpnProtocolTestServer() throws Exception {
        this(null);
    }

    AlpnProtocolTestServer(String redirectLocation) throws Exception {
        this(redirectLocation, true, false);
    }

    /**
     * @param redirectLocation if not null, answer every request with a 307 redirect to this location
     * @param selectH2 whether to select h2 when the client offers it, rather than behave like an HTTP/1.1-only server
     * @param requireClientCertificate whether to require the client certificate from {@link #clientKeyStorePath()}
     */
    AlpnProtocolTestServer(String redirectLocation, boolean selectH2, boolean requireClientCertificate)
        throws Exception {
        this.redirectLocation = redirectLocation;
        certificate = new SelfSignedCertificate("localhost");

        KeyStore serverStore = KeyStore.getInstance("PKCS12");
        serverStore.load(null, null);
        serverStore.setKeyEntry("server", certificate.key(), PASSWORD, new Certificate[] {certificate.cert()});
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(serverStore, PASSWORD);

        KeyStore clientStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = getClass().getResourceAsStream("/hostname-test-client.p12")) {
            clientStore.load(input, PASSWORD);
        }
        KeyStore trustedClients = KeyStore.getInstance("PKCS12");
        trustedClients.load(null, null);
        trustedClients.setCertificateEntry("client", clientStore.getCertificate("client"));
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustedClients);

        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);

        trustStorePath = Files.createTempFile("play-ws-alpn-trust-", ".p12");
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, PASSWORD);
        trustStore.setCertificateEntry("localhost", certificate.cert());
        try (var trustStoreOutput = Files.newOutputStream(trustStorePath)) {
            trustStore.store(trustStoreOutput, PASSWORD);
        }
        clientKeyStorePath = Files.createTempFile("play-ws-alpn-client-", ".p12");
        try (InputStream input = getClass().getResourceAsStream("/hostname-test-client.p12")) {
            Files.copy(input, clientKeyStorePath, StandardCopyOption.REPLACE_EXISTING);
        }

        serverChannel = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    clients.add(channel);
                    SSLEngine engine = serverContext.createSSLEngine();
                    engine.setUseClientMode(false);
                    SSLParameters parameters = engine.getSSLParameters();
                    parameters.setNeedClientAuth(requireClientCertificate);
                    // Let the client's cipher suite preference win, so that its configuration is observable.
                    parameters.setUseCipherSuitesOrder(false);
                    engine.setSSLParameters(parameters);
                    AtomicReference<List<String>> offered = new AtomicReference<>();
                    engine.setHandshakeApplicationProtocolSelector((sslEngine, clientProtocols) -> {
                        offered.set(List.copyOf(clientProtocols));
                        if (selectH2 && clientProtocols.contains(ApplicationProtocolNames.HTTP_2)) {
                            return ApplicationProtocolNames.HTTP_2;
                        }
                        // An empty string continues the handshake without selecting a protocol.
                        return clientProtocols.contains(ApplicationProtocolNames.HTTP_1_1)
                            ? ApplicationProtocolNames.HTTP_1_1
                            : "";
                    });
                    channel.pipeline()
                        .addLast(new SslHandler(engine))
                        .addLast(new HandshakeRecorder(engine, offered));
                }
            })
            .bind("127.0.0.1", 0)
            .sync()
            .channel();
    }

    String url() {
        return "https://localhost:" + ((InetSocketAddress) serverChannel.localAddress()).getPort() + "/";
    }

    /** The HTTP protocol of the last successful handshake: "h2" or "http/1.1". */
    String negotiatedProtocol() {
        Handshake handshake = lastHandshake.get();
        return handshake == null ? null : handshake.protocol();
    }

    /** The last successful handshake, or null. */
    Handshake lastHandshake() {
        return lastHandshake.get();
    }

    String trustStorePath() {
        return trustStorePath.toString();
    }

    /** A PKCS12 store, password "changeit", with the client certificate the server accepts. */
    String clientKeyStorePath() {
        return clientKeyStorePath.toString();
    }

    @Override
    public void close() {
        clients.close().awaitUninterruptibly();
        serverChannel.close().awaitUninterruptibly();
        group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
        certificate.delete();
        try {
            Files.deleteIfExists(trustStorePath);
            Files.deleteIfExists(clientKeyStorePath);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private final class HandshakeRecorder extends ChannelInboundHandlerAdapter {
        private final SSLEngine engine;
        private final AtomicReference<List<String>> offered;

        HandshakeRecorder(SSLEngine engine, AtomicReference<List<String>> offered) {
            this.engine = engine;
            this.offered = offered;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof SslHandshakeCompletionEvent completion) {
                if (!completion.isSuccess()) {
                    context.close();
                    return;
                }
                boolean h2 = ApplicationProtocolNames.HTTP_2.equals(engine.getApplicationProtocol());
                String protocol = h2 ? ApplicationProtocolNames.HTTP_2 : ApplicationProtocolNames.HTTP_1_1;
                SSLSession session = engine.getSession();
                String clientCertificate;
                try {
                    clientCertificate =
                        ((X509Certificate) session.getPeerCertificates()[0]).getSubjectX500Principal().getName();
                } catch (SSLPeerUnverifiedException e) {
                    clientCertificate = "none";
                }
                lastHandshake.set(new Handshake(offered.get(), protocol, session.getProtocol(),
                    session.getCipherSuite(), clientCertificate));
                if (h2) {
                    context.pipeline()
                        .addLast(Http2FrameCodecBuilder.forServer().build())
                        .addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(Http2StreamChannel stream) {
                                stream.pipeline().addLast(new Http2Responder());
                            }
                        }));
                } else {
                    context.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(1024))
                        .addLast(new Http1Responder());
                }
                context.pipeline().remove(this);
            }
            super.userEventTriggered(context, event);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            context.close();
        }
    }

    private final class Http1Responder extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
            var content = redirectLocation == null
                ? Unpooled.copiedBuffer(ApplicationProtocolNames.HTTP_1_1, StandardCharsets.UTF_8)
                : Unpooled.EMPTY_BUFFER;
            var response = new DefaultFullHttpResponse(HTTP_1_1, redirectLocation == null ? OK : TEMPORARY_REDIRECT,
                content);
            HttpUtil.setContentLength(response, content.readableBytes());
            if (redirectLocation != null) {
                response.headers().set(LOCATION, redirectLocation);
            }
            context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private final class Http2Responder extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext context, Object frame) {
            boolean requestEnded = frame instanceof Http2HeadersFrame headers && headers.isEndStream()
                || frame instanceof Http2DataFrame data && data.isEndStream();
            if (!requestEnded) {
                return;
            }
            if (redirectLocation != null) {
                Http2Headers headers = new DefaultHttp2Headers().status(TEMPORARY_REDIRECT.codeAsText());
                headers.set(LOCATION, redirectLocation).setInt("content-length", 0);
                context.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true));
                return;
            }
            ByteBuf body = Unpooled.copiedBuffer(ApplicationProtocolNames.HTTP_2, StandardCharsets.UTF_8);
            context.write(new DefaultHttp2HeadersFrame(
                new DefaultHttp2Headers().status(OK.codeAsText()).setInt("content-length", body.readableBytes()),
                false));
            context.writeAndFlush(new DefaultHttp2DataFrame(body, true));
        }
    }
}
