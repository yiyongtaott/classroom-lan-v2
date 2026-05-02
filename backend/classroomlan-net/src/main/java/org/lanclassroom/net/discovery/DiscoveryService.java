package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP 组播发现服务。
 * 职责：
 * 1. 启动时 join multicast group
 * 2. 每 2s 广播一次 HELLO（携带 nodeId / version / 当前 host 信仰 / hostname）
 * 3. 接收回路解析 HELLO → 更新 HostElector + 维护 ip↔node 关联表 + UDP 心跳计入 UserStatusService
 * 4. 周期判断：本机非 Host 时自动打开浏览器跳到 Host 的前端页面
 */
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
    /**
     * 用 ObjectProvider 避免与 UserStatusService 之间的循环依赖。
     */
    private final ObjectProvider<UserStatusService> userStatusProvider;
    private final ObjectMapper mapper = new ObjectMapper();
    /**
     * nodeId → 该节点的源 IP（用于打开 Host 前端页面）。
     */
    private final Map<String, String> nodeIpMap = new ConcurrentHashMap<>();
    /**
     * IP → 该节点的 hostname（Bug 10：用于在 host 端展示客户端的系统名）。
     */
    private final Map<String, String> ipHostnameMap = new ConcurrentHashMap<>();

    private volatile String openedForHostId = null;

    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private ScheduledExecutorService scheduler;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DiscoveryService(HostElector elector, ObjectProvider<UserStatusService> userStatusProvider, Room room,SimpMessagingTemplate messaging) {
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

        log.info("[Discovery] joined {}:{} on iface={} as nodeId={} (server :{})",
                groupAddress, port,
                networkInterface != null ? networkInterface.getName() : "default",
                NodeIdGenerator.getNodeId(),
                serverPort);
        // 注册 Host 变更监听器，实时通知所有客户端
        elector.addListener((isHost, hostId) -> {
            Map<String, Object> msg = Map.of(
                    "type", "HOST_CHANGED",
                    "newHostId", hostId,
                    "isSelf", isHost
            );
            messaging.convertAndSend("/topic/host", msg);
            log.info("[Discovery] broadcasted HOST_CHANGED: newHost={}, isSelf={}", hostId, isHost);
        });
        // 启动后立即触发一次重选举并广播自身状态
        elector.forceReelectAndNotify();
    }

    private void tick() {
        sendHello();
        // 自身 UDP 心跳也写入 UserStatusService（让 host 也有第一圆）
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
        String hostId = elector.electHost();

        if (hostId.equals(openedForHostId)) {
            return;
        }
        String targetIp;
        if (selfId.equals(hostId)) {
            targetIp = "localhost";
        } else {
            targetIp = nodeIpMap.get(hostId);
            if (targetIp == null) {
                return;
            }
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

    private void receiveLoop() {
        byte[] buffer = new byte[8192];
        while (running.get() && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = mapper.readValue(json, DiscoveryMessage.class);
                if (!msg.isHello() || msg.getId() == null) {
                    continue;
                }
                String senderIp = packet.getAddress().getHostAddress();
                if (!msg.getId().equals(NodeIdGenerator.getNodeId())) {
                    nodeIpMap.put(msg.getId(), senderIp);
                    if (msg.getHostname() != null) {
                        ipHostnameMap.put(senderIp, msg.getHostname());
                    }
                }
                elector.onPeer(msg.getId(), msg.getVersion(), msg.isHost(), msg.getHostname());

                // 任务 3 状态一：UDP 心跳计入
                UserStatusService userStatus = userStatusProvider.getIfAvailable();
                if (userStatus != null) {
                    String uuid = room.findByIp(senderIp).map(Player::getId).orElse(null);
                    if (uuid != null) {
                        userStatus.updateUdpHeartbeat(uuid, Instant.now());
                    }
                }
            } catch (Exception e) {
                if (running.get() && !socket.isClosed()) {
                    log.warn("[Discovery] receive error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Bug 10：通过 IP 查 hostname（让 host 知道客户端的系统名）。
     */
    public String hostnameByIp(String ip) {
        if (ip == null) return null;
        // 自身 IP → 返回本机 hostname
        if (ip.equals(NodeIdGenerator.getNodeId())) {
            return NodeIdGenerator.getHostname();
        }
        return ipHostnameMap.get(ip);
    }

    /**
     * 当前已知活跃节点 IP 集合（含 self），供玩家清理。
     */
    public java.util.Set<String> knownIps() {
        java.util.Set<String> set = new java.util.HashSet<>(nodeIpMap.values());
        set.add(NodeIdGenerator.getNodeId());
        // loopback 客户端也算 host 自身
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
            if (fallback == null) {
                fallback = ni;
            }
        }
        return fallback;
    }

    @Override
    public void destroy() {
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(new InetSocketAddress(group, port), networkInterface);
            } catch (Exception ignored) {
            }
            socket.close();
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        log.info("[Discovery] stopped");
    }
}
