package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.api.DiscoveryMessage;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
/**
 * UDP 组播发现服务 - 定时发送 HELLO，接收 HELLO/BEAT 参与 Host 选举
 */
@Service
public class DiscoveryService implements DisposableBean {
    private static final String GROUP_ADDR = "230.0.0.1";
    private static final int GROUP_PORT = 9999;

    private MulticastSocket socket;
    private InetAddress group;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ObjectMapper mapper = new ObjectMapper();

    private final HostElector hostElector;
    private final TokenService tokenService;
    private final Room room;

    // 记录收到的 Host 心跳时间（仅非 Host 节点使用）
    private final ConcurrentHashMap<String, Long> lastBeatFrom = new ConcurrentHashMap<>();
    private static final long BEAT_TIMEOUT_MS = 6000; // 3 个 HELLO 周期未收到心跳则认为 Host 离线

    public DiscoveryService(HostElector hostElector, TokenService tokenService, Room room) {
        this.hostElector = hostElector;
        this.tokenService = tokenService;
        this.room = room;
    }

    @PostConstruct
    public void start() throws Exception {
        // 1. 将网络 IO 操作移至 @PostConstruct，避免阻塞 Spring 实例化
        this.socket = new MulticastSocket(GROUP_PORT);
        this.group = InetAddress.getByName(GROUP_ADDR);
        this.socket.setReuseAddress(true);

        // 2. 自动寻找支持组播的真实物理网卡，避免绑定到 127.0.0.1 导致无法接收
        NetworkInterface networkInterface = getMulticastNetworkInterface();
        this.socket.joinGroup(new InetSocketAddress(group, GROUP_PORT), networkInterface);

        System.out.printf("[Discovery] Started on %s:%d using interface: %s%n",
                GROUP_ADDR, GROUP_PORT, networkInterface.getDisplayName());

        // 每 2 秒发送一次 HELLO
        scheduler.scheduleAtFixedRate(this::sendHello, 0, 2, TimeUnit.SECONDS);

        // 每 1 秒检查 Host 心跳超时
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 1, 1, TimeUnit.SECONDS);

        // 启动接收线程
        new Thread(this::receiveLoop, "udp-receiver").start();
    }

    /**
     * 自动寻找合适的物理网卡用于组播
     */
    private NetworkInterface getMulticastNetworkInterface() throws SocketException, UnknownHostException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();

            // 必须：开启状态、非回环、支持组播
            if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                // 关键修复：确保该网卡至少有一个 IPv4 地址
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 检查是否为 IPv4 地址
                    if (addr instanceof Inet4Address) {
                        System.out.println("[Discovery] Successfully bound to IPv4 interface: " + ni.getDisplayName());
                        return ni;
                    }
                }
            }
        }
        // 如果实在找不到，回退到默认（可能会抛异常，但这是最后的尝试）
        return NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
    }

    private void sendHello() {
        try {
            DiscoveryMessage msg = new DiscoveryMessage();
            msg.setType("HELLO");
            msg.setHost(hostElector.isHost());
            msg.setId(UUID.randomUUID().toString());
            msg.setNodeId(hostElector.getNodeId());
            if (hostElector.isHost()) {
                msg.setRoomKey(hostElector.getRoomKey());
            }

            byte[] payload = mapper.writeValueAsBytes(msg);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, group, GROUP_PORT);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("[Discovery] sendHello error: " + e.getMessage());
        }
    }

    private void receiveLoop() {

        byte[] buffer = new byte[2048];
        System.out.println("[Discovery] UDP Receiver thread started.");
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = mapper.readValue(payload, DiscoveryMessage.class);

                // 3. 核心修复：过滤掉自己发出的组播包，防止状态错乱
                if (msg.getNodeId() == null || msg.getNodeId().equals(hostElector.getNodeId())) {
                    continue;
                }

                handleDiscoveryMessage(msg);
            } catch (Exception e) {
                // 捕获单次解析异常，防止一个坏包导致整个接收线程崩溃
                if (!socket.isClosed()) {
                    System.err.println("[Discovery] receive payload error: " + e.getMessage());
                }
            }
        }
    }

    private void handleDiscoveryMessage(DiscoveryMessage msg) {
        String type = msg.getType();
        if ("HELLO".equals(type)) {
            onHello(msg);
        } else if ("BEAT".equals(type)) {
            onBeat(msg);
        }
    }

    private synchronized void onHello(DiscoveryMessage msg) {
        // 当前为 Host 且收到另一个节点的 HELLO
        if (hostElector.isHost()) {
            if (!msg.isHost()) {
                // 如果对方不是 Host，我作为 Host 发送 BEAT 镇压，宣告我的存在
                sendBeat();
            } else {
                // 如果对方也是 Host（脑裂情况），交给 HostElector 根据优先级裁决
                hostElector.onMessage(msg);
            }
        } else {
            // 当前不是 Host
            if (msg.isHost()) {
                // 收到现任 Host 的 HELLO，记录心跳并同步房间信息
                lastBeatFrom.put(msg.getNodeId(), System.currentTimeMillis());
                if (msg.getRoomKey() != null && !msg.getRoomKey().equals(room.getRoomKey())) {
                    room.setRoomKey(msg.getRoomKey());
                }
            }
            // 无论对方是不是 Host，都交给 HostElector 参与可能的选举评估
            hostElector.onMessage(msg);
        }
    }

    private synchronized void onBeat(DiscoveryMessage msg) {
        if (msg.isHost()) {
            // 仅记录声明自己是 Host 的 BEAT 心跳
            lastBeatFrom.put(msg.getNodeId(), System.currentTimeMillis());
            // 同步交给 Elector 处理（处理更高级别 Host 的抢占逻辑）
            hostElector.onMessage(msg);
        }
    }

    private void sendBeat() {
        try {
            DiscoveryMessage beat = new DiscoveryMessage();
            beat.setType("BEAT");
            beat.setHost(true);
            beat.setId(UUID.randomUUID().toString());
            beat.setNodeId(hostElector.getNodeId());
            beat.setRoomKey(hostElector.getRoomKey());

            byte[] payload = mapper.writeValueAsBytes(beat);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, group, GROUP_PORT);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("[Discovery] sendBeat error: " + e.getMessage());
        }
    }

    private void checkTimeouts() {
        // 4. 修复超时逻辑：只有非 Host 节点才需要监控 Host 是否超时
        if (hostElector.isHost()) {
            // 如果自己晋升为了 Host，清空对其他节点的监控
            if (!lastBeatFrom.isEmpty()) {
                lastBeatFrom.clear();
            }
            return;
        }

        long now = System.currentTimeMillis();
        // 使用 removeIf 安全地遍历并移除超时的记录
        lastBeatFrom.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > BEAT_TIMEOUT_MS) {
                System.out.println("[Discovery] Detected Host timeout, dead node: " + entry.getKey());
                // 重置房间状态，等待下一轮 HELLO 触发 HostElector 的新一轮选举
                room.setRoomKey(null);
                return true; // 移除该过期心跳记录
            }
            return false;
        });
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(new InetSocketAddress(group, GROUP_PORT), getMulticastNetworkInterface());
            } catch (Exception ignored) {}
            socket.close();
        }
        System.out.println("[Discovery] Service stopped.");
    }
}