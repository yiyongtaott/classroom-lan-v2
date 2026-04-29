package org.lanclassroom.net.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.api.DiscoveryMessage;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.net.*;
import java.nio.charset.StandardCharsets;
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

    private final MulticastSocket socket;
    private final InetAddress group;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ObjectMapper mapper = new ObjectMapper();

    private final HostElector hostElector;
    private final TokenService tokenService;
    private final Room room;

    // 心跳丢失阈值（3个周期未收到 BEAT 则认为 Host 挂掉）
    private final ConcurrentHashMap<String, Long> lastBeatFrom = new ConcurrentHashMap<>();
    private static final long BEAT_TIMEOUT_MS = 6000;

    public DiscoveryService(HostElector hostElector, TokenService tokenService, Room room) throws Exception {
        this.hostElector = hostElector;
        this.tokenService = tokenService;
        this.room = room;
        this.socket = new MulticastSocket(GROUP_PORT);
        this.group = InetAddress.getByName(GROUP_ADDR);
        this.socket.setReuseAddress(true);
        this.socket.joinGroup(new InetSocketAddress(group, GROUP_PORT),
                NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
        System.out.println("[Discovery] Started on " + GROUP_ADDR + ":" + GROUP_PORT);
    }

    @PostConstruct
    public void start() {
        // 每 2 秒发送一次 HELLO
        scheduler.scheduleAtFixedRate(this::sendHello, 0, 2, TimeUnit.SECONDS);

        // 每 1 秒检查 Host 心跳超时
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 1, 1, TimeUnit.SECONDS);

        // 启动接收线程
        new Thread(this::receiveLoop, "udp-receiver").start();
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
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = mapper.readValue(payload, DiscoveryMessage.class);

                handleDiscoveryMessage(msg);
            } catch (Exception ignored) {}
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
        System.out.printf("[Discovery] HELLO from node=%s, host=%s%n", msg.getNodeId(), msg.isHost());

        // 当前为 Host 且收到另一个节点的 HELLO
        if (hostElector.isHost() && !msg.isHost()) {
            // 发送 BEAT，宣布自己仍是 Host
            sendBeat();
        } else if (!hostElector.isHost() && msg.isHost()) {
            // 已有 Host，记录 Host 的 roomKey
            lastBeatFrom.put(msg.getNodeId(), System.currentTimeMillis());
            // 如果不是本房间的 Host，切换跟随
            if (!msg.getRoomKey().equals(room.getRoomKey())) {
                room.setRoomKey(msg.getRoomKey());
            }
            hostElector.onMessage(msg);
        }
    }

    private synchronized void onBeat(DiscoveryMessage msg) {
        // 仅 Host 发送 BEAT，非 Host 收到后更新心跳时间
        lastBeatFrom.put(msg.getNodeId(), System.currentTimeMillis());
        hostElector.onMessage(msg);
    }

    private void sendBeat() {
        try {
            DiscoveryMessage beat = new DiscoveryMessage();
            beat.setType("BEAT");
            beat.setHost(true);
            beat.setId(UUID.randomUUID().toString());
            beat.setNodeId(hostElector.getNodeId());
            beat.setRoomKey(room.getRoomKey());

            byte[] payload = mapper.writeValueAsBytes(beat);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, group, GROUP_PORT);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("[Discovery] sendBeat error: " + e.getMessage());
        }
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastBeatFrom.entrySet()) {
            if (now - entry.getValue() > BEAT_TIMEOUT_MS) {
                System.out.println("[Discovery] Host timeout, node=" + entry.getKey());
                lastBeatFrom.remove(entry.getKey());
                if (hostElector.isHost()) {
                    hostElector.onMessage(null); // 触发重新选举
                    // 清空房间（如果自己是原 Host）
                    room.setActive(false);
                }
                break;
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        scheduler.shutdownNow();
        socket.leaveGroup(group);
        socket.close();
    }
}
