package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.api.DiscoveryMessage;
import org.lanclassroom.net.service.UserStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Data
@RequiredArgsConstructor
public class DiscoveryService implements DisposableBean {

    public static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    public static final String VERSION = "v2";
    private final SimpMessagingTemplate messaging;
    private final Room room;

    @Value("${app.udp.multicast-group:230.0.0.1}")
    private String groupAddress;

    @Value("${app.udp.port:9999}")
    private int port;

    @Value("${app.udp.hello-interval-ms:2000}")
    private long helloIntervalMs;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${app.discovery.auto-open-browser:true}")
    private boolean autoOpenBrowser;

    private final HostElector elector;
    private final ObjectProvider<UserStatusService> userStatusProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, String> nodeIpMap = new ConcurrentHashMap<>();
    private final Map<String, String> ipHostnameMap = new ConcurrentHashMap<>();

    private volatile String openedForHostId = null;

    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private ScheduledExecutorService scheduler;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() throws Exception {
        this.group = InetAddress.getByName(groupAddress);
        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(1);
        this.networkInterface = pickInterface();
        this.socket.joinGroup(new InetSocketAddress(group, port), networkInterface);

        running.set(true);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "udp-hello-tx");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, helloIntervalMs, TimeUnit.MILLISECONDS);

        receiverThread = new Thread(this::receiveLoop, "udp-discovery-rx");
        receiverThread.setDaemon(true);
        receiverThread.start();

        log.info("[Discovery] joined {}:{} on iface={} as nodeId={} (server :{})",
                groupAddress, port,
                networkInterface != null ? networkInterface.getName() : "default",
                NodeIdGenerator.getNodeId(),
                serverPort);

        // 注册 Host 变更监听器
        elector.addListener((isHost, hostId) -> {
            Map<String, Object> msg = Map.of(
                    "type", "HOST_CHANGED",
                    "newHostId", hostId,
                    "isSelf", isHost
            );
            messaging.convertAndSend("/topic/host", msg);
            log.info("[Discovery] broadcasted HOST_CHANGED: newHost={}, isSelf={}", hostId, isHost);
        });

        // 启动快速选举
        performElection();
    }

    // ---------- 选举协议 ----------
    private void performElection() {
        try {
            // 1. 发送 HOST_QUERY
            DiscoveryMessage query = DiscoveryMessage.hostQuery(NodeIdGenerator.getNodeId());
            byte[] data = mapper.writeValueAsBytes(query);
            socket.send(new DatagramPacket(data, data.length, group, port));

            // 2. 等待 500ms 收集 HOST_REPLY
            long deadline = System.currentTimeMillis() + 500;
            String bestHost = null;
            byte[] buf = new byte[8192];
            int oldTimeout = socket.getSoTimeout();
            socket.setSoTimeout(100);
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    DiscoveryMessage msg = mapper.readValue(
                            new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8),
                            DiscoveryMessage.class);
                    if (msg != null && msg.getType() == DiscoveryMessage.Type.HOST_REPLY
                            && msg.getHostId() != null) {
                        if (bestHost == null || msg.getHostId().compareTo(bestHost) < 0) {
                            bestHost = msg.getHostId();
                        }
                    }
                } catch (SocketTimeoutException ignored) {}
            }
            socket.setSoTimeout(oldTimeout);

            if (bestHost != null) {
                elector.setHost(bestHost);
                log.info("[Election] discovered existing Host: {}", bestHost);
            } else {
                elector.setHost(NodeIdGenerator.getNodeId());
                broadcastHostClaim(NodeIdGenerator.getNodeId());
                log.info("[Election] no Host replied, claiming myself as Host");
            }
        } catch (Exception e) {
            log.warn("[Election] fallback to self", e);
            elector.setHost(NodeIdGenerator.getNodeId());
        }
    }

    private void broadcastHostClaim(String hostId) {
        DiscoveryMessage msg = DiscoveryMessage.hostClaim(NodeIdGenerator.getNodeId(), hostId);
        try {
            byte[] data = mapper.writeValueAsBytes(msg);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            log.warn("[Discovery] failed to send HOST_CLAIM: {}", e.getMessage());
        }
    }

    private void sendHostReply(String targetHostId) {
        DiscoveryMessage reply = DiscoveryMessage.hostReply(NodeIdGenerator.getNodeId(), targetHostId);
        try {
            byte[] data = mapper.writeValueAsBytes(reply);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            log.warn("[Discovery] failed to send HOST_REPLY: {}", e.getMessage());
        }
    }

    // ---------- 定期任务 ----------
    private void tick() {
        sendHello();
        UserStatusService userStatus = userStatusProvider.getIfAvailable();
        if (userStatus != null) {
            String selfIp = NodeIdGenerator.getNodeId();
            String uuid = room.findByIp(selfIp).map(Player::getId).orElse(null);
            if (uuid != null) {
                userStatus.updateUdpHeartbeat(uuid, Instant.now());
            }
        }
        if (autoOpenBrowser) {
            maybeOpenHostPage();
        }
    }

    private void sendHello() {
        try {
            DiscoveryMessage msg = DiscoveryMessage.hello(
                    NodeIdGenerator.getNodeId(),
                    VERSION,
                    elector.isHost(),
                    NodeIdGenerator.getHostname());
            byte[] data = mapper.writeValueAsBytes(msg);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            if (running.get()) {
                log.warn("[Discovery] send hello failed: {}", e.getMessage());
            }
        }
    }

    private void maybeOpenHostPage() {
        String selfId = NodeIdGenerator.getNodeId();
        String hostId = elector.getHostId();
        if (hostId == null || hostId.equals(openedForHostId)) return;

        String targetIp;
        if (selfId.equals(hostId)) {
            targetIp = "localhost";
        } else {
            targetIp = nodeIpMap.get(hostId);
            if (targetIp == null) return;
        }
        String url = "http://" + targetIp + ":" + serverPort + "/";
        if (openBrowser(url)) {
            openedForHostId = hostId;
            log.info("[Discovery] opened host page: {} (selfIsHost={})", url, selfId.equals(hostId));
        }
    }

    private boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception e) {
            log.debug("[Discovery] Desktop.browse failed: {}", e.getMessage());
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.inheritIO().start();
            return true;
        } catch (Exception e) {
            log.warn("[Discovery] open browser failed: {}", e.getMessage());
            return false;
        }
    }

    // ---------- 接收循环 ----------
    private void receiveLoop() {
        byte[] buffer = new byte[8192];
        while (running.get() && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = mapper.readValue(json, DiscoveryMessage.class);
                if (msg == null || msg.getId() == null) continue;
                String senderIp = packet.getAddress().getHostAddress();

                switch (msg.getType()) {
                    case HELLO:
                        handleHello(msg, senderIp);
                        break;
                    case HOST_QUERY:
                        handleHostQuery(msg);
                        break;
                    case HOST_REPLY:
                        // 只在 performElection 中处理，此处忽略
                        break;
                    case HOST_CLAIM:
                        handleHostClaim(msg, senderIp);
                        break;
                }
            } catch (Exception e) {
                if (running.get() && !socket.isClosed()) {
                    log.warn("[Discovery] receive error: {}", e.getMessage());
                }
            }
        }
    }

    private void handleHello(DiscoveryMessage msg, String senderIp) {
        if (msg.getId().equals(NodeIdGenerator.getNodeId())) return;

        nodeIpMap.put(msg.getId(), senderIp);
        if (msg.getHostname() != null) {
            ipHostnameMap.put(senderIp, msg.getHostname());
        }
        elector.onPeer(msg.getId(), msg.getVersion(), msg.isHost(), msg.getHostname());

        UserStatusService userStatus = userStatusProvider.getIfAvailable();
        if (userStatus != null) {
            String uuid = room.findByIp(senderIp).map(Player::getId).orElse(null);
            if (uuid != null) {
                userStatus.updateUdpHeartbeat(uuid, Instant.now());
            }
        }
    }

    private void handleHostQuery(DiscoveryMessage msg) {
        String currentHost = elector.getHostId();
        if (currentHost != null) {
            sendHostReply(currentHost);
        }
    }

    private void handleHostClaim(DiscoveryMessage msg, String senderIp) {
        String claimedHost = msg.getHostId();
        if (claimedHost == null) return;

        String selfId = NodeIdGenerator.getNodeId();
        if (elector.getHostId() == null) {
            elector.setHost(claimedHost);
        } else if (elector.isHost()) {
            if (claimedHost.compareTo(selfId) < 0) {
                elector.setHost(claimedHost);
                broadcastHostClaim(claimedHost);
            }
        } else {
            if (claimedHost.compareTo(elector.getHostId()) < 0) {
                elector.setHost(claimedHost);
            }
        }
    }

    // ---------- 工具方法 ----------
    public String hostnameByIp(String ip) {
        if (ip == null) return null;
        if (ip.equals(NodeIdGenerator.getNodeId())) {
            return NodeIdGenerator.getHostname();
        }
        return ipHostnameMap.get(ip);
    }

    public Set<String> knownIps() {
        Set<String> set = new HashSet<>(nodeIpMap.values());
        set.add(NodeIdGenerator.getNodeId());
        set.add("127.0.0.1");
        return set;
    }

    private NetworkInterface pickInterface() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        NetworkInterface fallback = null;
        while (ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            if (!ni.supportsMulticast()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return ni;
                }
            }
            if (fallback == null) fallback = ni;
        }
        return fallback;
    }

    @Override
    public void destroy() {
        if (elector.isHost()) {
            broadcastHostClaim(null); // 退位广播
        }
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(new InetSocketAddress(group, port), networkInterface);
            } catch (Exception ignored) {}
            socket.close();
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        log.info("[Discovery] stopped");
    }
}