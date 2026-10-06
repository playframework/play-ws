/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import play.shaded.ahc.io.netty.buffer.ByteBuf;
import play.shaded.ahc.org.asynchttpclient.request.body.Body;
import play.shaded.ahc.org.asynchttpclient.request.body.generator.FeedListener;
import play.shaded.ahc.org.asynchttpclient.request.body.generator.FeedableBodyGenerator;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal bridge from a Reactive Streams request body to AHC 3's feedable body API.
 */
public final class ReactiveStreamsBodyGenerator implements FeedableBodyGenerator {

    private final Publisher<ByteBuf> publisher;
    private final long contentLength;
    private final AtomicReference<ReactiveStreamsBody> activeBody = new AtomicReference<>();
    private volatile FeedListener listener;

    public ReactiveStreamsBodyGenerator(Publisher<ByteBuf> publisher, long contentLength) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.contentLength = contentLength;
    }

    @Override
    public Body createBody() {
        ReactiveStreamsBody body = new ReactiveStreamsBody(contentLength, this::signalContent);
        activeBody.set(body);
        publisher.subscribe(body);
        return body;
    }

    @Override
    public boolean feed(ByteBuf buffer, boolean isLast) {
        throw new UnsupportedOperationException("This body generator is fed by its Reactive Streams publisher");
    }

    @Override
    public void setListener(FeedListener listener) {
        this.listener = listener;
        ReactiveStreamsBody body = activeBody.get();
        if (body != null && body.isReady()) {
            signalContent(body.failure());
        }
    }

    private void signalContent(Throwable failure) {
        FeedListener currentListener = listener;
        if (currentListener != null) {
            if (failure != null) {
                currentListener.onError(failure);
            }
            currentListener.onContentAdded();
        }
    }

    private static final class ReactiveStreamsBody implements Body, Subscriber<ByteBuf> {

        private final long contentLength;
        private final java.util.function.Consumer<Throwable> contentSignal;

        private Subscription subscription;
        private ByteBuf current;
        private Throwable failure;
        private boolean completed;
        private boolean closed;

        private ReactiveStreamsBody(long contentLength, java.util.function.Consumer<Throwable> contentSignal) {
            this.contentLength = contentLength;
            this.contentSignal = contentSignal;
        }

        @Override
        public long getContentLength() {
            return contentLength;
        }

        @Override
        public synchronized void onSubscribe(Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            if (closed) {
                subscription.cancel();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(ByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            Throwable protocolFailure = null;
            synchronized (this) {
                if (closed) {
                    buffer.release();
                    return;
                }
                if (current != null) {
                    buffer.release();
                    protocolFailure = new IllegalStateException("Request body publisher produced without demand");
                    failure = protocolFailure;
                } else {
                    current = buffer;
                }
            }
            contentSignal.accept(protocolFailure);
        }

        @Override
        public void onError(Throwable failure) {
            Objects.requireNonNull(failure, "failure");
            synchronized (this) {
                if (closed || completed) {
                    return;
                }
                this.failure = failure;
                completed = true;
            }
            contentSignal.accept(failure);
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (closed || completed) {
                    return;
                }
                completed = true;
            }
            contentSignal.accept(null);
        }

        @Override
        public BodyState transferTo(ByteBuf target) throws IOException {
            Subscription requestMore = null;
            BodyState result;
            synchronized (this) {
                if (failure != null) {
                    throw new IOException("The request body stream failed", failure);
                }
                if (closed) {
                    return BodyState.STOP;
                }
                if (current == null) {
                    return completed ? BodyState.STOP : BodyState.SUSPEND;
                }

                int length = Math.min(target.writableBytes(), current.readableBytes());
                target.writeBytes(current, length);
                if (!current.isReadable()) {
                    current.release();
                    current = null;
                    if (!completed) {
                        requestMore = subscription;
                    }
                }
                result = completed && current == null ? BodyState.STOP : BodyState.CONTINUE;
            }

            if (requestMore != null) {
                requestMore.request(1);
            }
            return result;
        }

        @Override
        public void close() {
            Subscription currentSubscription;
            ByteBuf currentBuffer;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                currentSubscription = subscription;
                currentBuffer = current;
                current = null;
            }
            if (currentSubscription != null) {
                currentSubscription.cancel();
            }
            if (currentBuffer != null) {
                currentBuffer.release();
            }
        }

        private synchronized boolean isReady() {
            return current != null || completed || failure != null;
        }

        private synchronized Throwable failure() {
            return failure;
        }
    }
}
