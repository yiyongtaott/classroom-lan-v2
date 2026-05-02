package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 游戏邀请状态机（BUG-03 重构版）。
 *
 * 状态：
 *   PENDING   - 等待玩家响应
 *   ACTIVE    - 全员接受 / 任意 FORCE → 进入游戏
 *   CANCELLED - 超半数拒绝 / 超时 / 主动取消
 *
 * 响应类型：ACCEPT | DECLINE | FORCE
 *
 * 核心规则：
 *   - 发起人和其他用户响应权完全相同，可在 ACCEPT/DECLINE 间随意切换
 *   - 任意人 FORCE → 立即开始，参与者 = 当前为 ACCEPT 或 FORCE 的人
 *   - 超半数拒绝（rejectCount > total/2）→ 立即取消
 *   - 全员接受 → 自动开始，所有人参与
 *   - 30s 超时 → 取消
 *
 * 广播：
 *   - /topic/game.invitation         INVITATION_CREATED / INVITATION_CLOSED 等高层事件
 *   - /topic/game.invitation.state   实时投票计数 + 每人响应（按 userId）
 *   - /topic/game.start              {gameType, players}（参与者 id 列表）
 */
@Service
public class GameInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GameInvitationService.class);

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

    /** 发起邀请。已有未决邀请会被取消。 */
    public synchronized Invitation start(GameType type, String initiatorId) {
        if (current != null && STATE_PENDING.equals(current.state)) {
            current.state = STATE_CANCELLED;
            broadcastInvitation();
            broadcastInvitationState();
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
        broadcastInvitation();
        broadcastInvitationState();
        evaluate();
        return inv;
    }

    /** 玩家响应（可重复调用以切换 ACCEPT ↔ DECLINE）。 */
    public synchronized void respond(String playerId, String response) {
        if (current == null || !STATE_PENDING.equals(current.state)) return;
        if (playerId == null) return;
        if (!Set.of(R_ACCEPT, R_DECLINE, R_FORCE).contains(response)) return;
        current.responses.put(playerId, response);
        log.info("[Invitation] respond player={} response={}", playerId, response);
        // 立即广播投票状态，再评估推进
        broadcastInvitationState();
        evaluate();
    }

    public synchronized void cancel() {
        if (current == null) return;
        current.state = STATE_CANCELLED;
        log.info("[Invitation] cancelled id={}", current.id);
        broadcastInvitation();
        broadcastInvitationState();
        current = null;
    }

    /** 检查是否可以推进状态：FORCE / 全员接受 / 超半数拒绝。 */
    private void evaluate() {
        if (current == null || !STATE_PENDING.equals(current.state)) return;
        Map<String, String> rs = current.responses;

        Set<String> alive = aliveOnlinePlayerIds();
        if (alive.isEmpty()) return;

        // FORCE → 立即开始
        if (rs.values().stream().anyMatch(R_FORCE::equals)) {
            startActive("force");
            return;
        }

        long acceptCount = alive.stream().filter(pid -> R_ACCEPT.equals(rs.get(pid))).count();
        long rejectCount = alive.stream().filter(pid -> R_DECLINE.equals(rs.get(pid))).count();
        int total = alive.size();

        // 超半数拒绝（> total/2）→ 关闭
        if (rejectCount * 2 > total) {
            current.state = STATE_CANCELLED;
            log.info("[Invitation] majority decline id={} reject={}/{}", current.id, rejectCount, total);
            broadcastInvitation();
            broadcastInvitationState();
            current = null;
            return;
        }

        // 全员接受
        if (acceptCount == total) {
            startActive("all-accept");
        }
    }

    private void startActive(String reason) {
        try {
            current.state = STATE_ACTIVE;
            // 当前 ACCEPT/FORCE 状态的玩家是参与者
            List<String> participants = current.responses.entrySet().stream()
                    .filter(e -> R_ACCEPT.equals(e.getValue()) || R_FORCE.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            current.participants.clear();
            current.participants.addAll(participants);

            engine.startGame(GameType.valueOf(current.gameType));
            log.info("[Invitation] activated reason={} id={} participants={}",
                    reason, current.id, participants);

            // 广播 game.start 携带参与者 id 列表
            Map<String, Object> startMsg = new HashMap<>();
            startMsg.put("invitationId", current.id);
            startMsg.put("gameType", current.gameType);
            startMsg.put("players", participants);
            messaging.convertAndSend(WebSocketConfig.TOPIC_GAME_START, startMsg);
            broadcastInvitation();
            broadcastInvitationState();
        } catch (Exception e) {
            log.warn("[Invitation] startGame failed: {}", e.getMessage());
            current.state = STATE_CANCELLED;
            broadcastInvitation();
            broadcastInvitationState();
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
            broadcastInvitation();
            broadcastInvitationState();
            current = null;
        } else if (STATE_PENDING.equals(inv.state)) {
            // 玩家可能离线导致状态可推进 → re-evaluate
            evaluate();
        }
    }

    public Invitation getCurrent() {
        return current;
    }

    private Set<String> aliveOnlinePlayerIds() {
        return room.getPlayers().stream()
                .filter(p -> Player.STATUS_ONLINE.equals(p.getStatus()))
                .map(Player::getId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void broadcastInvitation() {
        Map<String, Object> payload = new HashMap<>();
        if (current == null) {
            payload.put("state", "NONE");
        } else {
            payload.put("id", current.id);
            payload.put("gameType", current.gameType);
            payload.put("initiatorPlayerId", current.initiatorPlayerId);
            payload.put("startTime", current.startTime);
            payload.put("state", current.state);
            payload.put("responses", current.responses);
            payload.put("participants", current.participants);
        }
        messaging.convertAndSend(WebSocketConfig.TOPIC_INVITATION, payload);
    }

    /** BUG-03：实时投票状态广播 - 提供给客户端面板做即时刷新。 */
    private void broadcastInvitationState() {
        Map<String, Object> payload = new HashMap<>();
        if (current == null) {
            payload.put("state", "NONE");
            messaging.convertAndSend(WebSocketConfig.TOPIC_INVITATION_STATE, payload);
            return;
        }
        Set<String> alive = aliveOnlinePlayerIds();
        Map<String, String> rs = current.responses;
        long accept = alive.stream().filter(pid -> R_ACCEPT.equals(rs.get(pid)) || R_FORCE.equals(rs.get(pid))).count();
        long decline = alive.stream().filter(pid -> R_DECLINE.equals(rs.get(pid))).count();

        payload.put("invitationId", current.id);
        payload.put("state", current.state);
        payload.put("responses", current.responses);
        payload.put("acceptCount", accept);
        payload.put("rejectCount", decline);
        payload.put("total", alive.size());
        messaging.convertAndSend(WebSocketConfig.TOPIC_INVITATION_STATE, payload);
    }

    public static class Invitation {
        public String id;
        public String gameType;
        public String initiatorPlayerId;
        public long startTime;
        public String state;
        public Map<String, String> responses = new ConcurrentHashMap<>();
        public java.util.List<String> participants = new java.util.concurrent.CopyOnWriteArrayList<>();

        public String getId() { return id; }
        public String getGameType() { return gameType; }
        public String getInitiatorPlayerId() { return initiatorPlayerId; }
        public long getStartTime() { return startTime; }
        public String getState() { return state; }
        public Map<String, String> getResponses() { return responses; }
        public java.util.List<String> getParticipants() { return participants; }
    }
}
