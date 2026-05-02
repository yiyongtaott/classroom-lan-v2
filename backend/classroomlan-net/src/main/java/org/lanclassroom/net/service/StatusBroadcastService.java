package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.discovery.HostElector;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局状态广播服务 - 替代前端轮询 /api/status + /api/room。
 *
 * 取消轮询后，前端连接到 /ws 时由 WebSocketEventListener 单播 init 快照；
 * 后续状态由本服务在变化时主动 push 到 /topic/status 与 /topic/room。
 *
 * 同时担任 BUG-01 双主守卫：
 *   每次广播 status 时，比对当前 host id 与上次缓存值，发生变化即推 /topic/host
 *   通知所有客户端，让旧 host 的页面跳转到新 host。
 */
@Service
public class StatusBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(StatusBroadcastService.class);

    private final HostElector elector;
    private final Room room;
    private final DiscoveryService discovery;
    private final SimpMessagingTemplate messaging;

    private final AtomicReference<String> lastHostId = new AtomicReference<>("");

    public StatusBroadcastService(HostElector elector, Room room,
                                  DiscoveryService discovery, SimpMessagingTemplate messaging) {
        this.elector = elector;
        this.room = room;
        this.discovery = discovery;
        this.messaging = messaging;
    }

    /** 构造给前端的 status payload（不依赖 HttpServletRequest）。 */
    public Map<String, Object> buildStatusFor(String accessorIp) {
        String hostIp = NodeIdGenerator.getNodeId();
        boolean accessorIsHost = isAccessorTheHost(accessorIp, hostIp);
        String accessorHostname = accessorIsHost
                ? NodeIdGenerator.getHostname()
                : discovery.hostnameByIp(accessorIp);

        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", accessorIsHost ? hostIp : accessorIp);
        result.put("hostname", accessorHostname);
        result.put("host", accessorIsHost);
        result.put("hostNodeId", elector.electHost());
        result.put("hostHostname", NodeIdGenerator.getHostname());
        result.put("peerCount", elector.peerCount());
        result.put("playerCount", room.getPlayers().size());
        result.put("gameType", room.getGameType());
        return result;
    }

    public Map<String, Object> buildRoomSnapshot() {
        room.setHostNodeId(elector.electHost());
        Map<String, Object> snap = new HashMap<>();
        snap.put("hostNodeId", elector.electHost());
        snap.put("gameType", room.getGameType());
        snap.put("players", List.copyOf(room.getPlayers()));
        snap.put("playerCount", room.getPlayers().size());
        return snap;
    }

    /** 广播全局 status（不带 accessorIp 视角，只广播 host/peer/playerCount 等共享字段）。 */
    public void broadcastStatus() {
        Map<String, Object> shared = new HashMap<>();
        shared.put("hostNodeId", elector.electHost());
        shared.put("hostHostname", NodeIdGenerator.getHostname());
        shared.put("peerCount", elector.peerCount());
        shared.put("playerCount", room.getPlayers().size());
        shared.put("gameType", room.getGameType());
        messaging.convertAndSend(WebSocketConfig.TOPIC_STATUS, shared);

        // 检测 host 变更 → 通知所有客户端
        String newHost = elector.electHost();
        String prev = lastHostId.getAndSet(newHost);
        if (!Objects.equals(prev, newHost) && !"".equals(prev)) {
            // host 变更通知
            Map<String, Object> hostMsg = new HashMap<>();
            hostMsg.put("type", "HOST_CHANGED");
            hostMsg.put("newHostId", newHost);
            String selfId = NodeIdGenerator.getNodeId();
            hostMsg.put("selfId", selfId);
            hostMsg.put("isSelf", selfId.equals(newHost));
            messaging.convertAndSend(WebSocketConfig.TOPIC_HOST_CHANGED, hostMsg);
            log.info("[StatusBroadcast] host changed {} → {}", prev, newHost);
        }
    }

    public void broadcastRoom() {
        messaging.convertAndSend(WebSocketConfig.TOPIC_ROOM, buildRoomSnapshot());
    }

    public void broadcastPlayers() {
        messaging.convertAndSend(WebSocketConfig.TOPIC_PLAYERS, room.getPlayers());
    }

    /** 单个用户改名 / 改头像 → 增量更新（BUG-02 修复）。 */
    public void broadcastUserUpdate(Player p) {
        if (p == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", p.getId());
        payload.put("name", p.getName());
        payload.put("avatar", p.getAvatar());
        payload.put("hostname", p.getHostname());
        payload.put("ip", p.getIp());
        payload.put("status", p.getStatus());
        messaging.convertAndSend(WebSocketConfig.TOPIC_USER_UPDATE, payload);
    }

    /** 周期性广播 host 状态（用于 host 选举完成后通知所有客户端）。 */
    @Scheduled(fixedRate = 3000)
    public void periodicBroadcast() {
        broadcastStatus();
    }

    private static boolean isAccessorTheHost(String accessorIp, String hostIp) {
        if (accessorIp == null) return false;
        if (accessorIp.equals(hostIp)) return true;
        try {
            return java.net.InetAddress.getByName(accessorIp).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
