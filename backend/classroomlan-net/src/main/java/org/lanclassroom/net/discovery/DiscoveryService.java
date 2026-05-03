package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
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
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Map<String, String> nodeIpMap = new ConcurrentHashMap<>();
    private final Map<String, String> ipHostnameMap = new ConcurrentHashMap<>();

    private volatile String openedForHostId = null;

    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private ScheduledExecutorService scheduler;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DiscoveryService(HostElector elector,
                            ObjectProvider<UserStatusService> userStatusProvider,
                            Room room,
                            SimpMessagingTemplate messaging) {
        this.elector = elector;
        this.userStatusProvider = userStatusProvider;
        this.room = room;
        this.messaging = messaging;
    }

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

        log.info("[发现服务] 已加入组播 {}:{} 网卡={} 本机节点ID={} (HTTP端口:{})",
                groupAddress, port,
                networkInterface != null ? networkInterface.getName() : "默认",
                NodeIdGenerator.getNodeId(),
                serverPort);

        elector.addListener((isHost, hostId) -> {
            Map<String, Object> msg = Map.of(
                    "type", "HOST_CHANGED",
                    "newHostId", hostId,
                    "isSelf", isHost
            );
            messaging.convertAndSend("/topic/host", msg);
            log.info("[发现服务] 广播主机变更: 新主机={}, 本机是否为主机={}", hostId, isHost);
        });

        performElection();
    }

    // ---------- 选举协议 ----------
    private void performElection() {
        try {
            DiscoveryMessage query = DiscoveryMessage.hostQuery(NodeIdGenerator.getNodeId());
            byte[] data = mapper.writeValueAsBytes(query);
            socket.send(new DatagramPacket(data, data.length, group, port));
            log.info("[发现服务] 已发送主机查询");

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
                        log.debug("[发现服务] 收到主机回复 来自={} 主机={}", packet.getAddress().getHostAddress(), msg.getHostId());
                        if (bestHost == null || msg.getHostId().compareTo(bestHost) < 0) {
                            bestHost = msg.getHostId();
                        }
                    }
                } catch (SocketTimeoutException ignored) {}
            }
            socket.setSoTimeout(oldTimeout);

            if (bestHost != null) {
                elector.setHost(bestHost);
                log.info("[发现服务] 选举完毕：接受现有主机 {}", bestHost);
            } else {
                elector.setHost(NodeIdGenerator.getNodeId());
                broadcastHostClaim(NodeIdGenerator.getNodeId());
                log.info("[发现服务] 选举完毕：未收到回复，自己成为主机");
            }
        } catch (Exception e) {
            log.warn("[发现服务] 选举失败，默认自举为主机", e);
            elector.setHost(NodeIdGenerator.getNodeId());
        }
    }

    // 广播主机宣告（声明自己或转发正确主机）
    private void broadcastHostClaim(String hostId) {
        log.info("[发现服务] 宣告主机: {}", hostId);
        DiscoveryMessage msg = DiscoveryMessage.hostClaim(NodeIdGenerator.getNodeId(), hostId);
        try {
            byte[] data = mapper.writeValueAsBytes(msg);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            log.warn("[发现服务] 发送主机宣告失败: {}", e.getMessage());
        }
    }

    // 广播主机死亡宣告（即将下线）
    private void broadcastHostBye() {
        log.info("[发现服务] 宣告自己即将下线（死亡宣告）");
        DiscoveryMessage msg = DiscoveryMessage.hostBye(NodeIdGenerator.getNodeId());
        try {
            byte[] data = mapper.writeValueAsBytes(msg);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            log.warn("[发现服务] 发送死亡宣告失败: {}", e.getMessage());
        }
    }

    // 回复主机查询
    private void sendHostReply(String targetHostId) {
        log.info("[发现服务] 回复主机查询：当前主机为 {}", targetHostId);
        DiscoveryMessage reply = DiscoveryMessage.hostReply(NodeIdGenerator.getNodeId(), targetHostId);
        try {
            byte[] data = mapper.writeValueAsBytes(reply);
            socket.send(new DatagramPacket(data, data.length, group, port));
        } catch (Exception e) {
            log.warn("[发现服务] 发送主机回复失败: {}", e.getMessage());
        }
    }

    // ---------- 定期任务 ----------
    private void tick() {
        sendHello();
        UserStatusService userStatus = userStatusProvider.getIfAvailable();
        if (userStatus != null) {
            String selfIp = NodeIdGenerator.getNodeId();
            room.findByIp(selfIp).map(Player::getId).ifPresent(uuid -> userStatus.updateUdpHeartbeat(uuid, Instant.now()));
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
            log.debug("[发现服务] 发送心跳 (是否主机={})", elector.isHost());
        } catch (Exception e) {
            if (running.get()) {
                log.warn("[发现服务] 发送心跳失败: {}", e.getMessage());
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
            log.info("[发现服务] 已打开主机页面: {} (本机是否为主机={})", url, selfId.equals(hostId));
        }
    }

    // 打开浏览器（保持不变）
    private boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception e) {
            log.debug("[发现服务] 调用系统浏览器失败: {}", e.getMessage());
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
            log.warn("[发现服务] 打开浏览器失败: {}", e.getMessage());
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
                        handleHostQuery(senderIp);
                        break;
                    case HOST_REPLY:
                        // 在 performElection 中处理，这里仅记录
                        log.debug("[发现服务] 收到主机回复 来自={} 主机={}", senderIp, msg.getHostId());
                        break;
                    case HOST_CLAIM:
                        handleHostClaim(msg, senderIp);
                        break;
                    case HOST_BYE:
                        handleHostBye(msg, senderIp);
                        break;
                }
            } catch (Exception e) {
                if (running.get() && !socket.isClosed()) {
                    log.warn("[发现服务] 接收数据异常: {}", e.getMessage());
                }
            }
        }
    }

    private void handleHello(DiscoveryMessage msg, String senderIp) {
        if (msg.getId().equals(NodeIdGenerator.getNodeId())) return;
        log.debug("[发现服务] 收到心跳 来自={} (是否主机={})", senderIp, msg.isHost());

        nodeIpMap.put(msg.getId(), senderIp);
        if (msg.getHostname() != null) {
            ipHostnameMap.put(senderIp, msg.getHostname());
        }
        elector.onPeer(msg.getId(), msg.getVersion(), msg.isHost(), msg.getHostname());

        UserStatusService userStatus = userStatusProvider.getIfAvailable();
        if (userStatus != null) {
            room.findByIp(senderIp).map(Player::getId).ifPresent(uuid -> userStatus.updateUdpHeartbeat(uuid, Instant.now()));
        }
    }

    private void handleHostQuery(String senderIp) {
        log.info("[发现服务] 收到主机查询 来自={}", senderIp);
        String currentHost = elector.getHostId();
        if (currentHost != null) {
            sendHostReply(currentHost);
        } else {
            log.debug("[发现服务] 尚无已知主机，忽略查询");
        }
    }

    private void handleHostClaim(DiscoveryMessage msg, String senderIp) {
        String claimedHost = msg.getHostId();
        if (claimedHost == null) {
            // 兼容旧版 null 声明（极少数情况）
            log.info("[发现服务] 收到退位广播（旧版）来自={}", senderIp);
            return;
        }
        log.info("[发现服务] 收到主机宣告 来自={} 主机={}", senderIp, claimedHost);

        String selfId = NodeIdGenerator.getNodeId();
        if (elector.getHostId() == null) {
            elector.setHost(claimedHost);
        } else if (elector.isHost()) {
            if (claimedHost.compareTo(selfId) < 0) {
                elector.setHost(claimedHost);
                broadcastHostClaim(claimedHost);
                log.info("[发现服务] 本机降级，新主机为 {}", claimedHost);
            }
        } else {
            if (claimedHost.compareTo(elector.getHostId()) < 0) {
                elector.setHost(claimedHost);
                log.info("[发现服务] 根据宣告更新主机为 {}", claimedHost);
            }
        }
    }

    private void handleHostBye(DiscoveryMessage msg, String senderIp) {
        log.info("[发现服务] 收到死亡宣告 来自={}，主机已下线", senderIp);
        // 立即删除已死亡主机的记录（如果有）
        elector.removePeer(msg.getId());
        // 重新选举
        String currentHost = elector.getHostId();
        if (currentHost != null && currentHost.equals(msg.getId())) {
            // 如果死亡的是当前主机，立即重新选举
            String selfId = NodeIdGenerator.getNodeId();
            // 找出存活节点中 IP 最小的
            String newHost = findSmallestAliveNode();
            if (newHost != null) {
                elector.setHost(newHost);
                if (newHost.equals(selfId)) {
                    broadcastHostClaim(newHost);
                }
                log.info("[发现服务] 重新选举完毕：新主机为 {}", newHost);
            } else {
                // 没有其他节点，自己成为主机
                elector.setHost(selfId);
                broadcastHostClaim(selfId);
                log.info("[发现服务] 无其他存活节点，自举为主机");
            }
        }
    }

    // 辅助方法：找出存活节点中 IP 最小的（作为新主机）
    private String findSmallestAliveNode() {
        String best = null;
        for (Map.Entry<String, String> entry : nodeIpMap.entrySet()) {
            String nodeId = entry.getKey();
            // 排除自己（等会儿会单独判断），但这里先包含
            if (best == null || nodeId.compareTo(best) < 0) {
                best = nodeId;
            }
        }
        // 如果只有一个节点（就是自己），best 可能是自己，再让调用方处理
        return best;
    }

    // ---------- 工具与资源管理 ----------
    public String hostnameByIp(String ip) {
        if (ip == null) return null;
        if (ip.equals(NodeIdGenerator.getNodeId())) {
            return NodeIdGenerator.getHostname();
        }
        return ipHostnameMap.get(ip);
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
            broadcastHostBye(); // 发送死亡宣告，不再用 null
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
        log.info("[发现服务] 已停止");
    }
}