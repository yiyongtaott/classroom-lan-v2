package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接跟踪器 - 维护 STOMP session ↔ playerId 映射，刷新 player.status。
 * 同时周期清理"后端 jar 已下线"的玩家（IP 已不在 DiscoveryService 已知节点列表里）。
 *
 * 状态流转：
 *   POST /api/room/players      → ONLINE (sessionCount=0, 等待 STOMP 上线 bind)
 *   /app/player.online          → ONLINE (sessionCount++)
 *   STOMP DISCONNECT            → 若 sessionCount=0 则 PAGE_CLOSED
 *   IP 从 DiscoveryService 消失 → OFFLINE → 从 Room 移除
 */
@Component
public class ConnectionTracker {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTracker.class);
    public static final String TOPIC_PLAYERS = "/topic/players";

    private final Room room;
    private final DiscoveryService discovery;
    private final SimpMessagingTemplate messaging;

    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();

    public ConnectionTracker(Room room, DiscoveryService discovery, SimpMessagingTemplate messaging) {
        this.room = room;
        this.discovery = discovery;
        this.messaging = messaging;
    }

    /** 客户端通过 /app/player.online 帧主动绑定。 */
    public void bind(String sessionId, String playerId) {
        if (sessionId == null || playerId == null) return;
        // 防重：同 session 已绑定 → 不重复 increment
        String prev = sessionToPlayer.put(sessionId, playerId);
        if (prev != null) {
            room.findById(prev).ifPresent(Player::decrementSession);
        }
        room.findById(playerId).ifPresent(Player::incrementSession);
        broadcastPlayers();
    }

    @EventListener
    public void onConnected(SessionConnectedEvent ev) {
        // 仅打印；真正的绑定靠 /app/player.online 帧
        log.debug("[ConnTrack] STOMP connected sid={}", ev.getMessage().getHeaders().get("simpSessionId"));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent ev) {
        String sid = ev.getSessionId();
        String pid = sessionToPlayer.remove(sid);
        if (pid != null) {
            room.findById(pid).ifPresent(p -> {
                p.decrementSession();
                log.debug("[ConnTrack] sid={} player={} → status={}", sid, pid, p.getStatus());
            });
            broadcastPlayers();
        }
    }

    /**
     * 每 4s 检查一次：
     *   - Player.ip 已不在 DiscoveryService 已知 IP 集合 → 后端 jar 已下线 → OFFLINE → 移除
     */
    @Scheduled(fixedRate = 4000)
    public void cleanup() {
        Set<String> alive = discovery.knownIps();
        boolean changed = false;
        for (Player p : new HashSet<>(room.getPlayers())) {
            if (p.getIp() != null && !alive.contains(p.getIp())) {
                log.info("[ConnTrack] removing offline player {} (ip={})", p.getName(), p.getIp());
                p.setStatus(Player.STATUS_OFFLINE);
                room.removePlayerById(p.getId());
                changed = true;
            }
        }
        if (changed) {
            broadcastPlayers();
        }
    }

    private void broadcastPlayers() {
        messaging.convertAndSend(TOPIC_PLAYERS, room.getPlayers());
    }
}
