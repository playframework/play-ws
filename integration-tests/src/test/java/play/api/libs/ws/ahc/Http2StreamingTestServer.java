/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import play.shaded.ahc.io.netty.bootstrap.ServerBootstrap;
import play.shaded.ahc.io.netty.buffer.ByteBuf;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.channel.ChannelFuture;
import play.shaded.ahc.io.netty.channel.ChannelHandlerContext;
import play.shaded.ahc.io.netty.channel.ChannelInitializer;
import play.shaded.ahc.io.netty.channel.MultiThreadIoEventLoopGroup;
import play.shaded.ahc.io.netty.channel.SimpleChannelInboundHandler;
import play.shaded.ahc.io.netty.channel.group.ChannelGroup;
import play.shaded.ahc.io.netty.channel.group.DefaultChannelGroup;
import play.shaded.ahc.io.netty.channel.nio.NioIoHandler;
import play.shaded.ahc.io.netty.channel.socket.nio.NioServerSocketChannel;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2Headers;
import play.shaded.ahc.io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2HeadersFrame;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2MultiplexHandler;
import play.shaded.ahc.io.netty.handler.codec.http2.Http2StreamChannel;
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig;
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolNames;
import play.shaded.ahc.io.netty.handler.ssl.SslContext;
import play.shaded.ahc.io.netty.handler.ssl.SslContextBuilder;
import play.shaded.ahc.io.netty.handler.ssl.util.SelfSignedCertificate;
import play.shaded.ahc.io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An HTTP/2 server for exercising Play WS streaming over a single parent connection. It speaks cleartext h2c with prior
 * knowledge, or, when constructed with TLS, h2 negotiated through ALPN.
 */
final class Http2StreamingTestServer implements AutoCloseable {
    private static final int FRAME_SIZE = 16 * 1024;
    /** 256 KiB: tests that suspend this response use a smaller stream window, so its last frame stays unsent. */
    private static final int SUSPENDED_FRAME_COUNT = 16;
    private static final int SIBLING_FRAME_COUNT = 64;

    private final AtomicInteger connectionCount = new AtomicInteger();
    private final CountDownLatch suspendedResponseQueued = new CountDownLatch(1);
    private final AtomicReference<ChannelFuture> suspendedResponseLastWrite = new AtomicReference<>();
    private final MultiThreadIoEventLoopGroup serverGroup =
        new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final ChannelGroup childChannels =
        new DefaultChannelGroup("play-ws-http2-streaming", GlobalEventExecutor.INSTANCE);
    private final Channel serverChannel;
    private final SelfSignedCertificate certificate;
    private final Path trustStorePath;

    Http2StreamingTestServer() throws Exception {
        this(false);
    }

    /** @param tls whether to serve h2 over TLS with a self-signed certificate for localhost */
    Http2StreamingTestServer(boolean tls) throws Exception {
        SslContext sslContext;
        if (tls) {
            certificate = new SelfSignedCertificate("localhost");
            trustStorePath = Files.createTempFile("play-ws-http2-trust-", ".p12");
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("localhost", certificate.cert());
            try (var output = Files.newOutputStream(trustStorePath)) {
                trustStore.store(output, "changeit".toCharArray());
            }
            sslContext = SslContextBuilder.forServer(certificate.key(), certificate.cert())
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2))
                .build();
        } else {
            certificate = null;
            trustStorePath = null;
            sslContext = null;
        }
        serverChannel =
            new ServerBootstrap()
                .group(serverGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        childChannels.add(channel);
                        connectionCount.incrementAndGet();
                        if (sslContext != null) {
                            channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
                        }
                        channel.pipeline()
                            .addLast(Http2FrameCodecBuilder.forServer().build())
                            .addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel streamChannel) {
                                    childChannels.add(streamChannel);
                                    streamChannel.pipeline().addLast(new StreamingHandler(suspendedResponseQueued, suspendedResponseLastWrite));
                                }
                            }));
                    }
                })
                .bind("127.0.0.1", 0)
                .sync()
                .channel();
    }

    String url(String path) {
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        return (certificate == null ? "http://127.0.0.1:" : "https://localhost:") + port + path;
    }

    /** A PKCS12 trust store, password "changeit", for the TLS server's certificate. */
    String trustStorePath() {
        return trustStorePath.toString();
    }

    int connectionCount() {
        return connectionCount.get();
    }

    long siblingResponseBytes() {
        return (long) FRAME_SIZE * SIBLING_FRAME_COUNT;
    }

    boolean awaitSuspendedResponseQueued(long timeout, TimeUnit unit) throws InterruptedException {
        return suspendedResponseQueued.await(timeout, unit);
    }

    /**
     * Whether the last frame of the suspended response is still waiting to be written, as HTTP/2 flow control keeps it
     * while the client does not return the stream's credit. False once that write has succeeded or failed.
     */
    boolean suspendedResponseStillPending() {
        ChannelFuture lastWrite = suspendedResponseLastWrite.get();
        return lastWrite != null && !lastWrite.isDone();
    }

    @Override
    public void close() {
        childChannels.close().awaitUninterruptibly();
        serverChannel.close().awaitUninterruptibly();
        serverGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
        if (certificate != null) {
            certificate.delete();
            try {
                Files.deleteIfExists(trustStorePath);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    private static final class StreamingHandler extends SimpleChannelInboundHandler<Object> {
        private final CountDownLatch suspendedResponseQueued;
        private final AtomicReference<ChannelFuture> suspendedResponseLastWrite;

        private StreamingHandler(
            CountDownLatch suspendedResponseQueued, AtomicReference<ChannelFuture> suspendedResponseLastWrite) {
            this.suspendedResponseQueued = suspendedResponseQueued;
            this.suspendedResponseLastWrite = suspendedResponseLastWrite;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, Object message) {
            if (!(message instanceof Http2HeadersFrame)) {
                return;
            }

            String path = ((Http2HeadersFrame) message).headers().path().toString();
            boolean suspendedResponse = "/suspended".equals(path);
            int frameCount = suspendedResponse ? SUSPENDED_FRAME_COUNT : SIBLING_FRAME_COUNT;
            long contentLength = (long) FRAME_SIZE * frameCount;
            context.write(new DefaultHttp2HeadersFrame(
                new DefaultHttp2Headers().status("200").setLong("content-length", contentLength), false));

            for (int frame = 0; frame < frameCount; frame++) {
                ByteBuf content = context.alloc().buffer(FRAME_SIZE).writeZero(FRAME_SIZE);
                boolean last = frame == frameCount - 1;
                if (last) {
                    ChannelFuture lastWrite = context.writeAndFlush(new DefaultHttp2DataFrame(content, true));
                    if (suspendedResponse) {
                        suspendedResponseLastWrite.set(lastWrite);
                    }
                } else {
                    context.write(new DefaultHttp2DataFrame(content, false));
                }
            }
            if (suspendedResponse) {
                suspendedResponseQueued.countDown();
            }
        }
    }
}
