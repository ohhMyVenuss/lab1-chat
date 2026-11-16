package vn.ltm.chat.client;

import vn.ltm.common.ChatWire;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class TcpChatClient implements AutoCloseable {
    private final Consumer<String> incoming;
    private final Consumer<String> status;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-tcp-reader"); t.setDaemon(true); return t;
    });
    private Socket socket;
    private BufferedWriter writer;

    TcpChatClient(Consumer<String> incoming, Consumer<String> status) {
        this.incoming = incoming; this.status = status;
    }

    void connect(String host, int port, String username) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        send(ChatWire.frame("LOGIN", username));
        status.accept("Connected to " + host + ":" + port + "; waiting for login acknowledgement...");
        reader.submit(() -> {
            try (input) {
                String line;
                while ((line = input.readLine()) != null) incoming.accept(line);
                status.accept("Server closed the connection.");
            } catch (IOException e) { status.accept("Connection lost: " + e.getMessage()); }
        });
    }

    synchronized void send(String frame) throws IOException {
        if (writer == null || socket == null || socket.isClosed()) throw new IOException("Not connected");
        writer.write(frame); writer.newLine(); writer.flush();
    }

    boolean isConnected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    @Override public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        reader.shutdownNow();
    }
}
