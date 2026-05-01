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

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP 组播发现服务。
 * 职责：
 *   1. 启动时 join multicast group
 *   2. 每 2s 广播一次 HELLO（携带本机 nodeId + version）
 *   3. 接收回路解析 HELLO → 更新 HostElector
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

    private final HostElector elector;
    private final ObjectMapper mapper = new ObjectMapper();

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
        scheduler.scheduleAtFixedRate(this::sendHello, 0, helloIntervalMs, TimeUnit.MILLISECONDS);

        receiverThread = new Thread(this::receiveLoop, "udp-discovery-rx");
        receiverThread.setDaemon(true);
        receiverThread.start();

        log.info("[Discovery] joined {}:{} on iface={} as nodeId={}",
                groupAddress, port,
                networkInterface != null ? networkInterface.getName() : "default",
                NodeIdGenerator.getNodeId());
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

    private void receiveLoop() {
        byte[] buffer = new byte[8192];
        while (running.get() && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = mapper.readValue(json, DiscoveryMessage.class);
                if (msg.isHello()) {
                    elector.onPeer(msg.getId(), msg.getVersion());
                }
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
