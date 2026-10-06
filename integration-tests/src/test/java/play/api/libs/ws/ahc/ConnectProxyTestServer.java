/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A plain HTTP proxy on 127.0.0.1 that only tunnels CONNECT requests, and records their targets. */
final class ConnectProxyTestServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> connectTargets = new CopyOnWriteArrayList<>();

    ConnectProxyTestServer() throws IOException {
        // Explicitly IPv4: InetAddress.getLoopbackAddress() may return ::1, while the tests connect to 127.0.0.1.
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        executor.execute(this::acceptConnections);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    /** The host:port targets of the CONNECT requests received so far. */
    List<String> connectTargets() {
        return List.copyOf(connectTargets);
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }

    private void acceptConnections() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                executor.execute(() -> tunnel(client));
            } catch (IOException e) {
                return; // closed
            }
        }
    }

    private void tunnel(Socket client) {
        try (client) {
            InputStream fromClient = client.getInputStream();
            OutputStream toClient = client.getOutputStream();
            String[] requestLine = readLine(fromClient).split(" ");
            while (!readLine(fromClient).isEmpty()) {
                // Skip the request headers.
            }
            if (requestLine.length < 2 || !"CONNECT".equals(requestLine[0])) {
                toClient.write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
                return;
            }
            String target = requestLine[1];
            connectTargets.add(target);
            int colon = target.lastIndexOf(':');
            try (Socket origin = connect(target.substring(0, colon), Integer.parseInt(target.substring(colon + 1)))) {
                toClient.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                toClient.flush();
                Future<?> upstream = executor.submit(() -> copy(fromClient, origin));
                copy(origin.getInputStream(), client);
                upstream.get();
            }
        } catch (Exception e) {
            // The client or the origin went away; the test asserts on the outcome.
        }
    }

    /** Tries every address of the host, because a test server may listen on only one of them. */
    private static Socket connect(String host, int port) throws IOException {
        IOException failure = null;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            try {
                return new Socket(address, port);
            } catch (IOException e) {
                failure = e;
            }
        }
        throw failure;
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1 && current != '\n') {
            if (current != '\r') {
                line.write(current);
            }
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    private static Void copy(InputStream input, Socket destination) throws IOException {
        try {
            input.transferTo(destination.getOutputStream());
        } finally {
            if (!destination.isClosed()) {
                destination.shutdownOutput();
            }
        }
        return null;
    }
}
