/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import play.shaded.ahc.io.netty.bootstrap.ServerBootstrap;
import play.shaded.ahc.io.netty.buffer.Unpooled;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.channel.ChannelFutureListener;
import play.shaded.ahc.io.netty.channel.ChannelHandlerContext;
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
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig;
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolNames;
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import play.shaded.ahc.io.netty.handler.ssl.SslContext;
import play.shaded.ahc.io.netty.handler.ssl.SslContextBuilder;
import play.shaded.ahc.io.netty.handler.ssl.util.SelfSignedCertificate;
import play.shaded.ahc.io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static play.shaded.ahc.io.netty.handler.codec.http.HttpResponseStatus.OK;
import static play.shaded.ahc.io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/** Offers both h2 and HTTP/1.1 so the Play WS client's actual TLS protocol choice is observable. */
final class AlpnProtocolTestServer implements AutoCloseable {
    private static final char[] TRUST_STORE_PASSWORD = "changeit".toCharArray();
    private final SelfSignedCertificate certificate;
    private final Path trustStorePath;
    private final MultiThreadIoEventLoopGroup group =
        new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final ChannelGroup clients =
        new DefaultChannelGroup("play-ws-alpn-protocol-test", GlobalEventExecutor.INSTANCE);
    private final AtomicReference<String> negotiatedProtocol = new AtomicReference<>();
    private final Channel serverChannel;

    AlpnProtocolTestServer() throws Exception {
        certificate = new SelfSignedCertificate("localhost");
        trustStorePath = Files.createTempFile("play-ws-alpn-trust-", ".p12");
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, TRUST_STORE_PASSWORD);
        try (var certificateInput = Files.newInputStream(certificate.certificate().toPath())) {
            trustStore.setCertificateEntry("localhost", CertificateFactory.getInstance("X.509")
                .generateCertificate(certificateInput));
        }
        try (var trustStoreOutput = Files.newOutputStream(trustStorePath)) {
            trustStore.store(trustStoreOutput, TRUST_STORE_PASSWORD);
        }
        SslContext sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
            .applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1))
            .build();

        serverChannel = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    clients.add(channel);
                    channel.pipeline()
                        .addLast(sslContext.newHandler(channel.alloc()))
                        .addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext context, String protocol) {
                                negotiatedProtocol.set(protocol);
                                if (!ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                                    context.close();
                                    return;
                                }
                                context.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(1024))
                                    .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
                                            var content = Unpooled.copiedBuffer(protocol, StandardCharsets.UTF_8);
                                            var response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
                                            HttpUtil.setContentLength(response, content.readableBytes());
                                            context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                        }
                                    });
                            }
                        });
                }
            })
            .bind("127.0.0.1", 0)
            .sync()
            .channel();
    }

    String url() {
        return "https://localhost:" + ((InetSocketAddress) serverChannel.localAddress()).getPort() + "/";
    }

    String negotiatedProtocol() {
        return negotiatedProtocol.get();
    }

    String trustStorePath() {
        return trustStorePath.toString();
    }

    @Override
    public void close() {
        clients.close().awaitUninterruptibly();
        serverChannel.close().awaitUninterruptibly();
        group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
        certificate.delete();
        try {
            Files.deleteIfExists(trustStorePath);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
