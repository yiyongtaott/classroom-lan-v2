package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.api.DiscoveryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
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
 *   1. 启动时 join multicast group
 *   2. 每 2s 广播一次 HELLO（携带本机 nodeId + version）
 *   3. 接收回路解析 HELLO → 更新 HostElector + 维护 nodeId→IP 映射
 *   4. 周期判断：本机非 Host 时自动打开浏览器跳到 Host 的前端页面
 */
@Service
public class DiscoveryService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    public static final String VERSION = "v2";

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
    private final ObjectMapper mapper = new ObjectMapper();
    /** 已知节点 → 最近一次发包的源 IP（用于打开 Host 前端页面）。 */
    private final Map<String, String> nodeIpMap = new ConcurrentHashMap<>();
    /** 已经为哪个 Host 打开过浏览器（避免重复打开）。null 表示未打开。 */
    private volatile String openedForHostId = null;

    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private ScheduledExecutorService scheduler;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DiscoveryService(HostElector elector) {
        this.elector = elector;
    }

    @PostConstruct
    public void start() throws Exception {
        this.group = InetAddress.getByName(groupAddress);
        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(1); // 仅本地段
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
    }

    /** 每个 tick：发 HELLO + 检查是否需要跳转到 Host 页面。 */
    private void tick() {
        sendHello();
        if (autoOpenBrowser) {
            maybeOpenHostPage();
        }
    }

    private void sendHello() {
        try {
            DiscoveryMessage msg = DiscoveryMessage.hello(NodeIdGenerator.getNodeId(), VERSION);
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

        // 已为当前 Host 打开过 → 跳过（含本机刚成为 Host 的情况）
        if (hostId.equals(openedForHostId)) {
            return;
        }
        // 决定打开的目标地址：
        //   selfId == hostId → 本机 Host，直接走 localhost（避免被代理或 hosts 干扰）
        //   否则用 nodeIpMap 拿到的远端 IP
        String targetIp;
        if (selfId.equals(hostId)) {
            targetIp = "localhost";
        } else {
            targetIp = nodeIpMap.get(hostId);
            if (targetIp == null) {
                return; // 还没收到该 Host 的报文，等下个 tick
            }
        }
        String url = "http://" + targetIp + ":" + serverPort + "/";
        if (openBrowser(url)) {
            openedForHostId = hostId;
            log.info("[Discovery] opened host page: {} (selfIsHost={})", url, selfId.equals(hostId));
        }
    }

    private boolean openBrowser(String url) {
        // 1) 优先用 Desktop API
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception e) {
            log.debug("[Discovery] Desktop.browse failed: {}", e.getMessage());
        }
        // 2) 退化到平台命令
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
                // 自己的回环报文不计入映射，但仍交给 elector（elector 内部会过滤 selfId）
                String senderIp = packet.getAddress().getHostAddress();
                if (!msg.getId().equals(NodeIdGenerator.getNodeId())) {
                    nodeIpMap.put(msg.getId(), senderIp);
                }
                elector.onPeer(msg.getId(), msg.getVersion());
            } catch (Exception e) {
                if (running.get() && !socket.isClosed()) {
                    log.warn("[Discovery] receive error: {}", e.getMessage());
                }
            }
        }
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
