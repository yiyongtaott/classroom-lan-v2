package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.discovery.DiscoveryService;
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

@Component
public class ConnectionTracker {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTracker.class);

    private static final long OFFLINE_GRACE_MS = 15_000; // 15 秒全灰宽限期

    private final Room room;
    private final DiscoveryService discovery;
    private final SimpMessagingTemplate messaging;
    private final UserStatusService userStatus;

    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    // 记录玩家首次进入全灰状态的时间戳（毫秒）
    private final Map<String, Long> offlineSince = new ConcurrentHashMap<>();

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
     * 每 4 秒检查一次所有玩家的三维状态。
     * 若 backendAlive、wsAlive、pageActive 连续 15 秒全部为 false，则移除该玩家。
     */
    @Scheduled(fixedRate = 4000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Player p : new HashSet<>(room.getPlayers())) {
            String pid = p.getId();
            if (pid == null) continue;

            UserStatusService.UserStatusRecord status = userStatus.getRecord(pid);
            boolean allOffline = status == null ||
                    (!status.isBackendAlive() && !status.isWsAlive() && !status.isPageActive());

            if (allOffline) {
                // 记录首次全灰时间（如果之前没记录）
                offlineSince.putIfAbsent(pid, now);
                long offlineStart = offlineSince.get(pid);
                if (now - offlineStart >= OFFLINE_GRACE_MS) {
                    log.info("[ConnTrack] removing idle player {} (ip={})", p.getName(), p.getIp());
                    p.setStatus(Player.STATUS_OFFLINE);
                    userStatus.setWsAlive(pid, false);
                    room.removePlayerById(pid);
                    offlineSince.remove(pid);
                    changed = true;
                }
            } else {
                // 任意状态活跃 → 清除全灰记录
                offlineSince.remove(pid);
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