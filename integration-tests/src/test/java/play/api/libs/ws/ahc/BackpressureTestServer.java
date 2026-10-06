/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A deliberately simple HTTP/1.1 server used to observe transport-level response backpressure. */
final class BackpressureTestServer implements AutoCloseable {
    private static final int CHUNK_SIZE = 16 * 1024;
    private static final long TOTAL_BYTES = 64L * 1024L * 1024L;

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
    private final AtomicLong bytesWritten = new AtomicLong();

    BackpressureTestServer() throws IOException {
        serverSocket = new ServerSocket(0);
        executor.execute(this::serve);
    }

    String url() {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/large";
    }

    long totalBytes() {
        return TOTAL_BYTES;
    }

    long bytesWritten() {
        return bytesWritten.get();
    }

    /**
     * Wait until the blocking writer has made no progress for {@code stableMillis}, or until the
     * overall timeout expires.
     */
    long awaitWriteStall(long timeoutMillis, long stableMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long last = bytesWritten.get();
        long stableSince = System.nanoTime();

        while (System.nanoTime() < deadline) {
            Thread.sleep(25);
            long current = bytesWritten.get();
            if (current != last) {
                last = current;
                stableSince = System.nanoTime();
            } else if (System.nanoTime() - stableSince >= TimeUnit.MILLISECONDS.toNanos(stableMillis)) {
                return current;
            }
        }
        return bytesWritten.get();
    }

    private void serve() {
        try (Socket socket = serverSocket.accept()) {
            acceptedSocket.set(socket);
            socket.setSendBufferSize(CHUNK_SIZE);
            socket.setTcpNoDelay(true);
            readHeaders(socket.getInputStream());

            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: " + TOTAL_BYTES + "\r\n"
                + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();

            byte[] chunk = new byte[CHUNK_SIZE];
            while (bytesWritten.get() < TOTAL_BYTES) {
                output.write(chunk);
                output.flush();
                bytesWritten.addAndGet(chunk.length);
            }
        } catch (IOException ignored) {
            // Closing the client or this server is expected to interrupt a blocked write.
        }
    }

    private static void readHeaders(InputStream input) throws IOException {
        int matched = 0;
        int[] terminator = {'\r', '\n', '\r', '\n'};
        while (matched < terminator.length) {
            int next = input.read();
            if (next < 0) {
                throw new IOException("Request ended before its headers were complete");
            }
            matched = next == terminator[matched] ? matched + 1 : next == terminator[0] ? 1 : 0;
        }
    }

    @Override
    public void close() throws IOException {
        Socket socket = acceptedSocket.get();
        if (socket != null) {
            socket.close();
        }
        serverSocket.close();
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
