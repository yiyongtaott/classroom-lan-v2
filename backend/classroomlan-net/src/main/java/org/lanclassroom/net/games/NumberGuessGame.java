package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 猜数字游戏 - 参考 GameSession 实现。
 *
 * 流程：
 *   start → Host 随机生成 1..100 之间的目标数；广播 STARTED 状态
 *   handleAction GUESS {value} → 高 / 低 / 中
 *   猜中 → 广播 WIN，自动 stop
 */
@Component
public class NumberGuessGame implements GameSession {

    private static final int LOWER = 1;
    private static final int UPPER = 100;

    private volatile boolean running;
    private int target;
    private int rounds;
    private Broadcaster broadcaster;
    private Room room;

    @Override
    public GameType getType() {
        return GameType.NUMBER_GUESS;
    }

    @Override
    public synchronized void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.target = ThreadLocalRandom.current().nextInt(LOWER, UPPER + 1);
        this.rounds = 0;
        this.running = true;
        broadcast("STARTED", Map.of("range", LOWER + "-" + UPPER));
    }

    @Override
    public synchronized void handleAction(Player player, Map<String, Object> payload) {
        if (!running) return;
        String action = String.valueOf(payload.getOrDefault("action", ""));
        if (!"GUESS".equals(action)) {
            return;
        }
        Object raw = payload.get("value");
        Integer guess = parseInt(raw);
        if (guess == null) {
            broadcast("INVALID", Map.of("playerId", safeId(player), "reason", "value missing or not int"));
            return;
        }
        rounds++;
        if (guess == target) {
            running = false;
            broadcast("WIN", Map.of(
                    "playerId", safeId(player),
                    "playerName", safeName(player),
                    "value", guess,
                    "rounds", rounds));
            // game ends but engine.stopCurrent() not called automatically — let host end it.
        } else if (guess < target) {
            broadcast("LOW", Map.of(
                    "playerId", safeId(player),
                    "playerName", safeName(player),
                    "value", guess,
                    "rounds", rounds));
        } else {
            broadcast("HIGH", Map.of(
                    "playerId", safeId(player),
                    "playerName", safeName(player),
                    "value", guess,
                    "rounds", rounds));
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (broadcaster != null) {
            broadcast("STOPPED", Map.of("rounds", rounds));
        }
    }

    private void broadcast(String stage, Map<String, Object> data) {
        if (broadcaster == null) return;
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("gameType", getType().name());
        envelope.put("stage", stage);
        envelope.put("data", data);
        envelope.put("playerCount", room == null ? 0 : room.getPlayers().size());
        envelope.put("ts", System.currentTimeMillis());
        broadcaster.broadcast(envelope);
    }

    private static Integer parseInt(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String safeId(Player p) {
        return p == null ? "unknown" : p.getId();
    }
    private static String safeName(Player p) {
        return p == null || p.getName() == null ? "玩家" : p.getName();
    }

    /* package-private getters for tests */
    int currentTarget() { return target; }
    boolean isRunning() { return running; }
}
