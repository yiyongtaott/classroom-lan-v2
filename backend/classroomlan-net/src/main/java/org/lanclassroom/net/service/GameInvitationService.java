package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 游戏邀请状态机（Bug 9）。
 *
 * 状态：
 *   PENDING   - 等待玩家响应
 *   ACTIVE    - 全员接受 / 任意 FORCE → 进入游戏
 *   CANCELLED - 全员拒绝 / 超时 / 主动取消
 *
 * 响应类型：ACCEPT | DECLINE | FORCE
 *
 * 离线玩家（status != ONLINE）不计入"全员"；
 * 仅活跃浏览器有响应权。
 */
@Service
public class GameInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GameInvitationService.class);

    public static final String TOPIC = "/topic/game.invitation";
    public static final long TIMEOUT_MS = 30_000L;

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_CANCELLED = "CANCELLED";

    public static final String R_ACCEPT = "ACCEPT";
    public static final String R_DECLINE = "DECLINE";
    public static final String R_FORCE = "FORCE";

    private final Room room;
    private final GameEngine engine;
    private final SimpMessagingTemplate messaging;

    private volatile Invitation current;

    public GameInvitationService(Room room, GameEngine engine, SimpMessagingTemplate messaging) {
        this.room = room;
        this.engine = engine;
        this.messaging = messaging;
    }

    public synchronized Invitation start(GameType type, String initiatorId) {
        if (current != null && STATE_PENDING.equals(current.state)) {
            // 已有待决邀请 → 取消旧的
            current.state = STATE_CANCELLED;
            broadcastState();
        }
        Invitation inv = new Invitation();
        inv.id = UUID.randomUUID().toString();
        inv.gameType = type.name();
        inv.initiatorPlayerId = initiatorId;
        inv.startTime = Instant.now().toEpochMilli();
        inv.state = STATE_PENDING;
        if (initiatorId != null) {
            inv.responses.put(initiatorId, R_ACCEPT); // 发起者默认接受
        }
        current = inv;
        log.info("[Invitation] start type={} initiator={} id={}", type, initiatorId, inv.id);
        broadcastState();
        evaluate();
        return inv;
    }

    public synchronized void respond(String playerId, String response) {
        if (current == null || !STATE_PENDING.equals(current.state)) return;
        if (playerId == null) return;
        if (!Set.of(R_ACCEPT, R_DECLINE, R_FORCE).contains(response)) return;
        current.responses.put(playerId, response);
        log.info("[Invitation] respond player={} response={}", playerId, response);
        evaluate();
        broadcastState();
    }

    public synchronized void cancel() {
        if (current == null) return;
        current.state = STATE_CANCELLED;
        log.info("[Invitation] cancelled id={}", current.id);
        broadcastState();
        current = null;
    }

    /** 检查是否可以推进状态（全员接受 / 任意 force / 全员拒绝）。 */
    private void evaluate() {
        if (current == null || !STATE_PENDING.equals(current.state)) return;
        Map<String, String> rs = current.responses;

        // 任意 FORCE → 立即开始
        if (rs.values().stream().anyMatch(R_FORCE::equals)) {
            startActive("force");
            return;
        }

        Set<String> alive = room.getPlayers().stream()
                .filter(p -> Player.STATUS_ONLINE.equals(p.getStatus()))
                .map(Player::getId)
                .collect(Collectors.toSet());

        if (alive.isEmpty()) return;

        boolean allAccept = alive.stream().allMatch(pid -> R_ACCEPT.equals(rs.get(pid)));
        if (allAccept) {
            startActive("all-accept");
            return;
        }
        boolean allDecline = alive.stream().allMatch(pid -> R_DECLINE.equals(rs.get(pid)));
        if (allDecline) {
            current.state = STATE_CANCELLED;
            log.info("[Invitation] all declined id={}", current.id);
            current = null;
        }
    }

    private void startActive(String reason) {
        try {
            current.state = STATE_ACTIVE;
            engine.startGame(GameType.valueOf(current.gameType));
            log.info("[Invitation] activated reason={} id={}", reason, current.id);
        } catch (Exception e) {
            log.warn("[Invitation] startGame failed: {}", e.getMessage());
            current.state = STATE_CANCELLED;
        }
    }

    @Scheduled(fixedRate = 3000)
    public void timeoutCheck() {
        Invitation inv = current;
        if (inv == null) return;
        if (STATE_PENDING.equals(inv.state)
                && Instant.now().toEpochMilli() - inv.startTime > TIMEOUT_MS) {
            log.info("[Invitation] timeout id={}", inv.id);
            inv.state = STATE_CANCELLED;
            broadcastState();
            current = null;
        }
        // 即使 PENDING 但因玩家离线导致状态可推进 → re-evaluate
        evaluate();
    }

    public Invitation getCurrent() {
        Invitation c = current;
        return c == null ? null : c;
    }

    private void broadcastState() {
        messaging.convertAndSend(TOPIC, current == null ? Map.of("state", "NONE") : current);
    }

    /** 邀请数据结构（供 Jackson 序列化为 JSON）。 */
    public static class Invitation {
        public String id;
        public String gameType;
        public String initiatorPlayerId;
        public long startTime;
        public String state;
        public Map<String, String> responses = new ConcurrentHashMap<>();

        public String getId() { return id; }
        public String getGameType() { return gameType; }
        public String getInitiatorPlayerId() { return initiatorPlayerId; }
        public long getStartTime() { return startTime; }
        public String getState() { return state; }
        public Map<String, String> getResponses() { return responses; }
    }
}
