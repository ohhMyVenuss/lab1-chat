package vn.ltm.chat.server;

import vn.ltm.common.ChatWire;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP chat hub + UDP broadcast receiver/relay. Start on the designated Server PC. */
public final class ChatServerMain {
    private ChatServerMain() { }

    public static void main(String[] args) throws Exception {
        Properties p = loadProperties(Path.of("config", "server-machine.properties"));
        String bind = p.getProperty("chat.bind.address", "0.0.0.0");
        int tcpPort = Integer.parseInt(p.getProperty("chat.tcp.port", "5000"));
        int udpPort = Integer.parseInt(p.getProperty("chat.udp.port", "5001"));
        String broadcastAddress = p.getProperty("chat.broadcast.address", "255.255.255.255");
        Path attachmentRoot = Path.of(p.getProperty("chat.files.directory", "data/chat-files")).toAbsolutePath().normalize();
        long maxAttachmentBytes = Long.parseLong(p.getProperty("chat.attachment.max.bytes", "6291456"));
        Files.createDirectories(attachmentRoot);
        ChatRepository repository = new ChatRepository(
                System.getenv("LTM_CHAT_DB_URL"), System.getenv("LTM_CHAT_DB_USER"), System.getenv("LTM_CHAT_DB_PASSWORD"));
        if (repository.enabled()) {
            try {
                repository.initialize();
                System.out.println("[DB] MySQL persistence enabled.");
            } catch (Exception databaseError) {
                repository.disablePersistence();
                System.err.println("[WARN] MySQL unavailable; switching to disk-only fallback: " + databaseError.getMessage());
            }
        } else System.out.println("[WARN] MySQL persistence disabled. Set LTM_CHAT_DB_URL/USER/PASSWORD to enable it.");
        ChatServer server = new ChatServer(bind, tcpPort, udpPort, broadcastAddress, repository, attachmentRoot, maxAttachmentBytes);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "chat-server-shutdown"));
        server.start();
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) try (var in = Files.newInputStream(path)) { properties.load(in); }
        else System.out.println("[WARN] Config not found: " + path.toAbsolutePath() + ". Using safe defaults.");
        return properties;
    }

    private static final class ChatServer {
        private final String bindAddress;
        private final int tcpPort;
        private final int udpPort;
        private final String broadcastAddress;
        private final ChatRepository repository;
        private final Path attachmentRoot;
        private final long maxAttachmentBytes;
        private final ConcurrentHashMap<String, ClientSession> clients = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        private final AtomicBoolean running = new AtomicBoolean(true);

        private ChatServer(String bindAddress, int tcpPort, int udpPort, String broadcastAddress,
                           ChatRepository repository, Path attachmentRoot, long maxAttachmentBytes) {
            this.bindAddress = bindAddress; this.tcpPort = tcpPort; this.udpPort = udpPort;
            this.broadcastAddress = broadcastAddress; this.repository = repository;
            this.attachmentRoot = attachmentRoot; this.maxAttachmentBytes = maxAttachmentBytes;
        }

        void start() throws IOException {
            startUdp();
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), tcpPort));
                System.out.printf("[CHAT] TCP listening on %s:%d; UDP relay on %s:%d%n", bindAddress, tcpPort, bindAddress, udpPort);
                while (running.get()) {
                    Socket socket = server.accept();
                    socket.setTcpNoDelay(true);
                    workers.submit(() -> new ClientSession(socket).run());
                }
            } finally { shutdown(); }
        }

        private void startUdp() {
            workers.submit(() -> {
                try (DatagramSocket socket = new DatagramSocket(null)) {
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), udpPort));
                    byte[] buffer = new byte[8192];
                    System.out.println("[CHAT] UDP broadcast relay ready on port " + udpPort);
                    while (running.get()) {
                        DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                        socket.receive(incoming);
                        String frame = new String(incoming.getData(), incoming.getOffset(), incoming.getLength(), StandardCharsets.UTF_8);
                        String[] parts = ChatWire.split(frame);
                        if (parts.length == 3 && "BCAST".equals(parts[0])) {
                            String sender = ChatWire.field(parts, 1);
                            String body = ChatWire.field(parts, 2);
                            if (sender.isBlank() || body.isBlank()) continue;
                            String outgoing = ChatWire.frame("BCAST_DELIVERY", sender, body);
                            byte[] data = outgoing.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket delivery = new DatagramPacket(data, data.length,
                                    InetAddress.getByName(broadcastAddress), udpPort);
                            socket.send(delivery);
                            repository.saveMessage(sender, null, "UDP_BROADCAST", body, Instant.now());
                            System.out.printf("[UDP-BROADCAST] %s: %s (%s)%n", sender, body, incoming.getAddress());
                        }
                    }
                } catch (SocketException e) {
                    if (running.get()) System.err.println("[UDP] Socket error: " + e.getMessage());
                } catch (Exception e) {
                    if (running.get()) System.err.println("[UDP] Relay stopped: " + e.getMessage());
                }
            });
        }

        private void sendUsers() {
            List<String> names = new ArrayList<>(clients.keySet());
            names.sort(Comparator.naturalOrder());
            String csv = String.join(",", names);
            String frame = ChatWire.frame("USERS", csv);
            clients.values().forEach(c -> c.send(frame));
        }

        private void broadcastEvent(String type, String from, String to, String body) {
            String frame = eventFrame(type, from, to, body);
            clients.values().forEach(c -> c.send(frame));
        }

        private String eventFrame(String type, String from, String to, String body) {
            return ChatWire.frame("EVENT", type, from, to == null ? "" : to, body, Instant.now().toString());
        }

        private void shutdown() {
            running.set(false);
            clients.values().forEach(ClientSession::close);
            workers.shutdownNow();
        }

        private final class ClientSession {
            private final Socket socket;
            private BufferedReader reader;
            private BufferedWriter writer;
            private volatile String username;
            private final AtomicBoolean open = new AtomicBoolean(true);
            private final AtomicBoolean cleaned = new AtomicBoolean(false);

            private ClientSession(Socket socket) { this.socket = socket; }

            void run() {
                try (socket;
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                    reader = in; writer = out;
                    send(ChatWire.frame("HELLO", "LTM Chat Server"));
                    String line;
                    while (open.get() && (line = reader.readLine()) != null) handle(line);
                } catch (IOException e) {
                    if (username != null) System.out.println("[TCP] " + username + " disconnected: " + e.getMessage());
                } finally { disconnect(); }
            }

            private void handle(String line) {
                try {
                    String[] p = ChatWire.split(line);
                    switch (p[0]) {
                        case "LOGIN" -> login(ChatWire.field(p, 1));
                        case "BROADCAST" -> sendBroadcast(ChatWire.field(p, 1));
                        case "PRIVATE" -> sendPrivate(ChatWire.field(p, 1), ChatWire.field(p, 2));
                        case "JOIN_GROUP" -> joinGroup(ChatWire.field(p, 1));
                        case "GROUP" -> sendGroup(ChatWire.field(p, 1), ChatWire.field(p, 2));
                        case "ATTACH" -> sendAttachment(ChatWire.field(p, 1), ChatWire.field(p, 2),
                                ChatWire.field(p, 3), ChatWire.field(p, 4), ChatWire.field(p, 5), ChatWire.field(p, 6));
                        case "PING" -> send(ChatWire.frame("PONG", Instant.now().toString()));
                        default -> send(ChatWire.frame("ERROR", "Unknown command: " + p[0]));
                    }
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
                    send(ChatWire.frame("ERROR", message.replace('\r', ' ').replace('\n', ' ')));
                }
            }

            private void sendAttachment(String scope, String target, String kind, String suppliedName,
                                        String contentType, String encodedPayload) throws IOException {
                requireLogin();
                String route = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
                String type = kind == null ? "FILE" : kind.trim().toUpperCase(Locale.ROOT);
                if (!Set.of("BROADCAST", "PRIVATE", "GROUP").contains(route)) {
                    throw new IOException("Attachment chỉ hỗ trợ Broadcast/Private/Group qua TCP.");
                }
                if (!Set.of("FILE", "IMAGE", "AUDIO", "VIDEO").contains(type)) {
                    throw new IOException("Loại tệp đính kèm không hợp lệ.");
                }
                if (encodedPayload == null || encodedPayload.length() > maxAttachmentBytes * 2) {
                    throw new IOException("Tệp quá lớn; giới hạn " + (maxAttachmentBytes / 1024 / 1024) + " MB.");
                }
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(encodedPayload); }
                catch (IllegalArgumentException exception) { throw new IOException("Dữ liệu tệp không hợp lệ."); }
                if (bytes.length == 0 || bytes.length > maxAttachmentBytes) {
                    throw new IOException("Tệp phải từ 1 byte đến " + (maxAttachmentBytes / 1024 / 1024) + " MB.");
                }

                String destination = target == null ? "" : target.trim();
                List<ClientSession> recipients = new ArrayList<>();
                if (route.equals("BROADCAST")) {
                    recipients.addAll(clients.values());
                } else if (route.equals("PRIVATE")) {
                    ClientSession other = clients.get(destination);
                    if (other == null) throw new IOException("Người nhận đang offline: " + destination);
                    recipients.add(this);
                    if (other != this) recipients.add(other);
                } else {
                    Set<String> members = groups.get(destination);
                    if (members == null || !members.contains(username)) throw new IOException("Hãy tham gia nhóm trước khi gửi tệp.");
                    for (String member : members) {
                        ClientSession client = clients.get(member);
                        if (client != null) recipients.add(client);
                    }
                }
                if (recipients.isEmpty()) throw new IOException("Không có người nhận trực tuyến.");

                String fileName = safeFileName(suppliedName);
                String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
                Path stored = attachmentRoot.resolve(UUID.randomUUID() + "-" + fileName).normalize();
                if (!stored.startsWith(attachmentRoot)) throw new IOException("Tên tệp không hợp lệ.");
                Files.write(stored, bytes);
                repository.saveAttachment(username, destination.isBlank() ? null : destination, route,
                        fileName, mime, stored.toString(), bytes.length);
                String event = ChatWire.frame("ATTACH_EVENT", route, username, destination, type,
                        fileName, mime, encodedPayload, Instant.now().toString());
                recipients.forEach(client -> client.send(event));
                System.out.printf("[ATTACH] %s -> %s (%s, %d bytes)%n", username, route, fileName, bytes.length);
            }

            private String safeFileName(String supplied) throws IOException {
                if (supplied == null || supplied.isBlank()) throw new IOException("Tên tệp không hợp lệ.");
                String name = supplied.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
                if (name.isBlank() || name.equals(".") || name.equals("..")) throw new IOException("Tên tệp không hợp lệ.");
                return name.length() > 160 ? name.substring(name.length() - 160) : name;
            }

            private void login(String requested) {
                if (username != null) { send(ChatWire.frame("ERROR", "Already logged in")); return; }
                String clean = requested == null ? "" : requested.trim();
                if (!clean.matches("[A-Za-z0-9_-]{2,32}")) {
                    send(ChatWire.frame("ERROR", "Username must be 2-32 chars: letters, digits, _ or -")); return;
                }
                ClientSession previous = clients.putIfAbsent(clean, this);
                if (previous != null) { send(ChatWire.frame("ERROR", "Username is already online")); return; }
                username = clean;
                repository.userSeen(clean);
                send(ChatWire.frame("LOGIN_OK", clean));
                sendUsers();
                broadcastEvent("SYSTEM", "SERVER", "", clean + " joined the chat");
                System.out.println("[TCP] " + clean + " connected from " + socket.getRemoteSocketAddress());
            }

            private void requireLogin() {
                if (username == null) throw new IllegalStateException("Login first");
            }

            private void sendBroadcast(String body) {
                requireLogin();
                if (body.isBlank()) return;
                repository.saveMessage(username, null, "TCP_BROADCAST", body, Instant.now());
                broadcastEvent("BROADCAST", username, "", body);
            }

            private void sendPrivate(String target, String body) {
                requireLogin();
                ClientSession recipient = clients.get(target);
                if (recipient == null) { send(ChatWire.frame("ERROR", "User is offline: " + target)); return; }
                if (body.isBlank()) return;
                repository.saveMessage(username, target, "PRIVATE", body, Instant.now());
                String frame = eventFrame("PRIVATE", username, target, body);
                send(frame); recipient.send(frame);
            }

            private void joinGroup(String group) {
                requireLogin();
                if (!group.matches("[A-Za-z0-9_-]{2,32}")) { send(ChatWire.frame("ERROR", "Invalid group name")); return; }
                groups.computeIfAbsent(group, ignored -> ConcurrentHashMap.newKeySet()).add(username);
                send(ChatWire.frame("JOINED_GROUP", group));
            }

            private void sendGroup(String group, String body) {
                requireLogin();
                Set<String> members = groups.get(group);
                if (members == null || !members.contains(username)) { send(ChatWire.frame("ERROR", "Join the group first")); return; }
                if (body.isBlank()) return;
                repository.saveMessage(username, group, "GROUP", body, Instant.now());
                String frame = eventFrame("GROUP", username, group, body);
                for (String member : members) {
                    ClientSession target = clients.get(member);
                    if (target != null) target.send(frame);
                }
            }

            synchronized void send(String frame) {
                if (!open.get() || writer == null) return;
                try { writer.write(frame); writer.newLine(); writer.flush(); }
                catch (IOException e) { close(); }
            }

            void close() {
                open.set(false);
                try { socket.close(); } catch (IOException ignored) { }
            }

            private void disconnect() {
                open.set(false);
                if (!cleaned.compareAndSet(false, true)) return;
                if (username != null) {
                    clients.remove(username, this);
                    groups.values().forEach(members -> members.remove(username));
                    broadcastEvent("SYSTEM", "SERVER", "", username + " left the chat");
                    sendUsers();
                }
            }
        }
    }
}
