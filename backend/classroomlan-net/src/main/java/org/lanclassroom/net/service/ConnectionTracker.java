package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.ws.ClientSessionRegistry;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家会话计数器 - 维护 STOMP session ↔ playerId 映射，刷新 player.status。
 *
 * 与 {@link ClientSessionRegistry} 的区别：
 *   - ClientSessionRegistry 只维护 session ↔ ip
 *   - ConnectionTracker 维护 session ↔ playerId（业务身份层）
 *
 * 玩家上线流程：
 *   POST /api/room/players                → 创建 Player（status=ONLINE, sessionCount=0）
 *   /app/player.online {playerId}         → bind(sessionId, playerId)，sessionCount++
 *   STOMP DISCONNECT                      → decrementSession，若为 0 则 PAGE_CLOSED
 *   IP 离开 DiscoveryService 已知集合     → 从 Room 移除
 */
@Component
public class ConnectionTracker {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTracker.class);

    private final Room room;
    private final DiscoveryService discovery;
    private final SimpMessagingTemplate messaging;
    private final UserStatusService userStatus;

    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();

    public ConnectionTracker(Room room, DiscoveryService discovery,
                             SimpMessagingTemplate messaging,
                             UserStatusService userStatus) {
        this.room = room;
        this.discovery = discovery;
        this.messaging = messaging;
        this.userStatus = userStatus;
    }

    /** 客户端通过 /app/player.online 帧主动绑定。 */
    public void bind(String sessionId, String playerId) {
        if (sessionId == null || playerId == null) return;
        // 防重：同 session 已绑定 → 不重复 increment
        String prev = sessionToPlayer.put(sessionId, playerId);
        if (prev != null) {
            room.findById(prev).ifPresent(Player::decrementSession);
        }
        room.findById(playerId).ifPresent(p -> {
            p.incrementSession();
            userStatus.setWsAlive(p.getId(), true);
        });
        broadcastPlayers();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent ev) {
        String sid = ev.getSessionId();
        String pid = sessionToPlayer.remove(sid);
        if (pid != null) {
            room.findById(pid).ifPresent(p -> {
                p.decrementSession();
                if (p.getSessionCount() == 0) {
                    userStatus.setWsAlive(p.getId(), false);
                }
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
                userStatus.setWsAlive(p.getId(), false);
                room.removePlayerById(p.getId());
                changed = true;
            }
        }
        if (changed) {
            broadcastPlayers();
        }
    }

    public void broadcastPlayers() {
        messaging.convertAndSend(WebSocketConfig.TOPIC_PLAYERS, room.getPlayers());
    }
}
