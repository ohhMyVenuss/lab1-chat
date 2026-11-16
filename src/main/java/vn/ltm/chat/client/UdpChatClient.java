package vn.ltm.chat.client;

import vn.ltm.common.ChatWire;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Real UDP broadcast relay and IP multicast peer channel (best effort delivery). */
final class UdpChatClient implements AutoCloseable {
    private final Consumer<String> incoming;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chat-udp"); t.setDaemon(true); return t;
    });
    private DatagramSocket broadcastSocket;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private NetworkInterface multicastInterface;
    private int multicastPort;

    UdpChatClient(Consumer<String> incoming) { this.incoming = incoming; }

    void start(int listenPort, String groupAddress, int groupPort) throws IOException {
        broadcastSocket = new DatagramSocket(null);
        broadcastSocket.setReuseAddress(true);
        broadcastSocket.setBroadcast(true);
        broadcastSocket.bind(new InetSocketAddress(listenPort));
        workers.submit(() -> listenBroadcast());

        multicastGroup = InetAddress.getByName(groupAddress);
        multicastPort = groupPort;
        multicastSocket = new MulticastSocket(null);
        multicastSocket.setReuseAddress(true);
        multicastSocket.bind(new InetSocketAddress(groupPort));
        multicastSocket.setTimeToLive(1);
        multicastInterface = selectMulticastInterface();
        multicastSocket.setNetworkInterface(multicastInterface);
        multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, multicastPort), multicastInterface);
        workers.submit(() -> listenMulticast());
    }

    void sendBroadcast(String serverHost, int serverUdpPort, String username, String body) throws IOException {
        ensureStarted();
        byte[] data = ChatWire.frame("BCAST", username, body).getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(serverHost), serverUdpPort);
        broadcastSocket.send(packet);
    }

    void sendMulticast(String username, String body) throws IOException {
        ensureStarted();
        byte[] data = ChatWire.frame("MULTICAST", username, body).getBytes(StandardCharsets.UTF_8);
        multicastSocket.send(new DatagramPacket(data, data.length, multicastGroup, multicastPort));
    }

    private void listenBroadcast() {
        byte[] buffer = new byte[8192];
        while (broadcastSocket != null && !broadcastSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                broadcastSocket.receive(packet);
                String frame = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String[] parts = ChatWire.split(frame);
                if (parts.length == 3 && "BCAST_DELIVERY".equals(parts[0]))
                    incoming.accept("[UDP BROADCAST] " + ChatWire.field(parts, 1) + ": " + ChatWire.field(parts, 2));
            } catch (SocketException e) { break; }
            catch (IOException e) { incoming.accept("[UDP] Receive error: " + e.getMessage()); }
        }
    }

    private void listenMulticast() {
        byte[] buffer = new byte[8192];
        while (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);
                String frame = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String[] parts = ChatWire.split(frame);
                if (parts.length == 3 && "MULTICAST".equals(parts[0]))
                    incoming.accept("[IP MULTICAST] " + ChatWire.field(parts, 1) + ": " + ChatWire.field(parts, 2));
            } catch (SocketException e) { break; }
            catch (IOException e) { incoming.accept("[MULTICAST] Receive error: " + e.getMessage()); }
        }
    }

    private NetworkInterface selectMulticastInterface() throws IOException {
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) throw new IOException("No network interfaces are available for multicast");
        for (NetworkInterface candidate : Collections.list(interfaces)) {
            try {
                if (candidate.isUp() && !candidate.isLoopback() && candidate.supportsMulticast()) return candidate;
            } catch (SocketException ignored) { }
        }
        throw new IOException("No active network interface supports IP multicast. Check Wi-Fi/Ethernet settings.");
    }

    private void ensureStarted() throws IOException {
        if (broadcastSocket == null || multicastSocket == null) throw new IOException("UDP services are not started");
    }

    @Override public void close() {
        if (broadcastSocket != null) broadcastSocket.close();
        if (multicastSocket != null) {
            try { multicastSocket.leaveGroup(new InetSocketAddress(multicastGroup, multicastPort), multicastInterface); }
            catch (IOException ignored) { }
            multicastSocket.close();
        }
        workers.shutdownNow();
    }
}
