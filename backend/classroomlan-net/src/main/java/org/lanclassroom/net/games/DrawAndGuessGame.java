package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.lanclassroom.net.ws.ClientSessionRegistry;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FEATURE-01：你画我猜。
 *
 * 流程：
 *   start()            → SELECTING：随机抽 3 个候选词，单播给当前 drawer
 *   /app/game.draw.select  drawer 选词 → DRAWING：广播 ROUND_START（含轮次过期时间戳）
 *   /app/game.draw.stroke  drawer 笔画批量推送 → 转发到 /topic/game.draw.canvas
 *   /app/game.draw.guess   guesser 猜词 → 答对则得分；广播 GUESS_CORRECT（不含答案明文）
 *   60s 超时 / 任意人猜中 → ROUND_END，自动换 drawer
 *   全员都做过 drawer 一次 → GAME_OVER
 */
@Component
public class DrawAndGuessGame implements GameSession {

    private static final Logger log = LoggerFactory.getLogger(DrawAndGuessGame.class);
    public static final int ROUND_TIME_SECONDS = 60;
    public static final String TOPIC_CANVAS = "/topic/game.draw.canvas";

    /** 简易词库 - 后续可外置到配置文件。 */
    private static final List<String> DICTIONARY = List.of(
            "苹果", "香蕉", "汽车", "飞机", "电视", "电脑", "手机", "书本",
            "太阳", "月亮", "星星", "云朵", "雨伞", "雪人", "树木", "花朵",
            "猫咪", "小狗", "兔子", "老鼠", "大象", "老虎", "熊猫", "海豚",
            "桌子", "椅子", "床铺", "门窗", "钥匙", "锁头", "时钟", "镜子"
    );

    private final SimpMessagingTemplate messaging;
    private final ClientSessionRegistry sessions;

    private volatile boolean running;
    private Room room;
    private Broadcaster broadcaster;
    private final List<String> drawerOrder = new CopyOnWriteArrayList<>();
    private int currentDrawerIdx = -1;
    private String currentDrawerId;
    private String currentWord;
    private List<String> wordOptions = List.of();
    private long roundStartTs;
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private String phase = "WAITING"; // WAITING | SELECTING | DRAWING | ROUND_END | GAME_OVER

    public DrawAndGuessGame(SimpMessagingTemplate messaging,
                            ClientSessionRegistry sessions) {
        this.messaging = messaging;
        this.sessions = sessions;
    }

    @Override
    public GameType getType() {
        return GameType.DRAW;
    }

    @Override
    public synchronized void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.running = true;
        this.scores.clear();
        this.drawerOrder.clear();
        for (Player p : room.getPlayers()) {
            if (Player.STATUS_ONLINE.equals(p.getStatus())) {
                drawerOrder.add(p.getId());
                scores.put(p.getId(), 0);
            }
        }
        this.currentDrawerIdx = -1;
        nextRound();
    }

    @Override
    public synchronized void handleAction(Player player, Map<String, Object> payload) {
        if (!running || player == null || payload == null) return;
        String action = String.valueOf(payload.getOrDefault("action", ""));
        switch (action) {
            case "SELECT" -> handleSelect(player, payload);
            case "STROKE" -> handleStroke(player, payload);
            case "GUESS" -> handleGuess(player, payload);
            case "CLEAR" -> handleClearCanvas(player);
            default -> log.debug("[Draw] unknown action: {}", action);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        phase = "GAME_OVER";
        broadcastEnvelope("GAME_OVER", Map.of("scores", scores));
    }

    private void handleSelect(Player drawer, Map<String, Object> payload) {
        if (!"SELECTING".equals(phase)) return;
        if (!Objects.equals(drawer.getId(), currentDrawerId)) return;
        Object idx = payload.get("index");
        int i = idx instanceof Number ? ((Number) idx).intValue() : 0;
        if (i < 0 || i >= wordOptions.size()) return;
        currentWord = wordOptions.get(i);
        phase = "DRAWING";
        roundStartTs = System.currentTimeMillis();
        broadcastEnvelope("ROUND_START", Map.of(
                "drawerId", currentDrawerId,
                "drawerName", drawer.getName(),
                "wordLength", currentWord.length(),
                "roundStartTs", roundStartTs,
                "roundEndTs", roundStartTs + ROUND_TIME_SECONDS * 1000L
        ));
        // 单播给 drawer 答案
        sendPrivate(currentDrawerId, Map.of(
                "type", "WORD_REVEAL",
                "word", currentWord
        ));
    }

    private void handleStroke(Player drawer, Map<String, Object> payload) {
        if (!"DRAWING".equals(phase)) return;
        if (!Objects.equals(drawer.getId(), currentDrawerId)) return;
        // 服务端只做转发，不做笔画几何校验。
        Map<String, Object> stroke = new HashMap<>(payload);
        stroke.remove("playerId");
        stroke.put("ts", System.currentTimeMillis());
        messaging.convertAndSend(TOPIC_CANVAS, stroke);
    }

    private void handleClearCanvas(Player drawer) {
        if (!"DRAWING".equals(phase)) return;
        if (!Objects.equals(drawer.getId(), currentDrawerId)) return;
        messaging.convertAndSend(TOPIC_CANVAS, Map.of("type", "CLEAR", "ts", System.currentTimeMillis()));
    }

    private void handleGuess(Player guesser, Map<String, Object> payload) {
        if (!"DRAWING".equals(phase)) return;
        if (Objects.equals(guesser.getId(), currentDrawerId)) return;
        Object g = payload.get("guess");
        String guess = g == null ? "" : String.valueOf(g).trim();
        if (guess.isEmpty()) return;

        boolean correct = currentWord != null && currentWord.equals(guess);
        if (correct) {
            scores.merge(guesser.getId(), 10, Integer::sum);
            scores.merge(currentDrawerId, 5, Integer::sum);
            broadcastEnvelope("GUESS_CORRECT", Map.of(
                    "guesserId", guesser.getId(),
                    "guesserName", guesser.getName(),
                    "scores", scores
            ));
            endRound("guess");
        } else {
            // 错误猜测仅广播给非 drawer 的人；drawer 看到的是聚合提示
            broadcastEnvelope("GUESS_WRONG", Map.of(
                    "guesserId", guesser.getId(),
                    "guesserName", guesser.getName(),
                    "guess", guess
            ));
        }
    }

    private void endRound(String reason) {
        broadcastEnvelope("ROUND_END", Map.of(
                "reason", reason,
                "drawerId", currentDrawerId,
                "word", currentWord,
                "scores", scores
        ));
        if (currentDrawerIdx >= drawerOrder.size() - 1) {
            // 所有人都画过一轮 → 游戏结束
            broadcastEnvelope("GAME_OVER", Map.of("scores", scores));
            running = false;
            phase = "GAME_OVER";
        } else {
            nextRound();
        }
    }

    private void nextRound() {
        currentDrawerIdx++;
        if (currentDrawerIdx >= drawerOrder.size()) {
            broadcastEnvelope("GAME_OVER", Map.of("scores", scores));
            running = false;
            phase = "GAME_OVER";
            return;
        }
        currentDrawerId = drawerOrder.get(currentDrawerIdx);
        wordOptions = pickWords(3);
        currentWord = null;
        phase = "SELECTING";
        Player drawer = room.findById(currentDrawerId).orElse(null);
        broadcastEnvelope("SELECTING", Map.of(
                "drawerId", currentDrawerId,
                "drawerName", drawer == null ? "?" : drawer.getName()
        ));
        sendPrivate(currentDrawerId, Map.of(
                "type", "WORD_OPTIONS",
                "options", wordOptions
        ));
    }

    private List<String> pickWords(int n) {
        List<String> all = new ArrayList<>(DICTIONARY);
        java.util.Collections.shuffle(all, ThreadLocalRandom.current());
        return all.subList(0, Math.min(n, all.size()));
    }

    private void broadcastEnvelope(String stage, Map<String, Object> data) {
        if (broadcaster == null) return;
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("gameType", getType().name());
        envelope.put("stage", stage);
        envelope.put("data", data);
        envelope.put("ts", System.currentTimeMillis());
        broadcaster.broadcast(envelope);
    }

    /** 单播到 player 的所有 session。 */
    private void sendPrivate(String playerId, Map<String, Object> body) {
        if (room == null) return;
        Player p = room.findById(playerId).orElse(null);
        if (p == null || p.getIp() == null) return;
        for (String sid : sessions.getSessionsByIp(p.getIp())) {
            SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create();
            h.setSessionId(sid);
            h.setLeaveMutable(true);
            messaging.convertAndSendToUser(sid, WebSocketConfig.QUEUE_DRAW_PRIVATE, body, h.getMessageHeaders());
        }
    }

    /** 后端定时检查超时 - 由 GameEngine 周期调用 update。这里直接用一个 watchdog 替代。 */
    public synchronized void watchdog() {
        if (!running || !"DRAWING".equals(phase)) return;
        if (System.currentTimeMillis() - roundStartTs > ROUND_TIME_SECONDS * 1000L) {
            endRound("timeout");
        }
    }
}
