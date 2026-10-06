/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import play.shaded.ahc.io.netty.bootstrap.ServerBootstrap;
import play.shaded.ahc.io.netty.buffer.ByteBuf;
import play.shaded.ahc.io.netty.channel.Channel;
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
import play.shaded.ahc.io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A cleartext HTTP/2 server for exercising Play WS streaming over a single parent connection. */
final class Http2StreamingTestServer implements AutoCloseable {
    private static final int FRAME_SIZE = 16 * 1024;
    private static final int SUSPENDED_FRAME_COUNT = 16;
    private static final int SIBLING_FRAME_COUNT = 64;

    private final AtomicInteger connectionCount = new AtomicInteger();
    private final CountDownLatch suspendedResponseQueued = new CountDownLatch(1);
    private final MultiThreadIoEventLoopGroup serverGroup =
        new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final ChannelGroup childChannels =
        new DefaultChannelGroup("play-ws-http2-streaming", GlobalEventExecutor.INSTANCE);
    private final Channel serverChannel;

    Http2StreamingTestServer() throws InterruptedException {
        serverChannel =
            new ServerBootstrap()
                .group(serverGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        childChannels.add(channel);
                        connectionCount.incrementAndGet();
                        channel.pipeline()
                            .addLast(Http2FrameCodecBuilder.forServer().build())
                            .addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel streamChannel) {
                                    childChannels.add(streamChannel);
                                    streamChannel.pipeline().addLast(new StreamingHandler(suspendedResponseQueued));
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
        return "http://127.0.0.1:" + port + path;
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

    @Override
    public void close() {
        childChannels.close().awaitUninterruptibly();
        serverChannel.close().awaitUninterruptibly();
        serverGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    private static final class StreamingHandler extends SimpleChannelInboundHandler<Object> {
        private final CountDownLatch suspendedResponseQueued;

        private StreamingHandler(CountDownLatch suspendedResponseQueued) {
            this.suspendedResponseQueued = suspendedResponseQueued;
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
                    context.writeAndFlush(new DefaultHttp2DataFrame(content, true));
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
