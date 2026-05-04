package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lanclassroom.net.games.*;

/**
 * 游戏引擎 - 注册中心 + 调度器。
 *
 * 启动时收集所有 GameSession Bean 按 type 索引；
 * 每次 startGame(type) 把指定 session 与 Room/Broadcaster 绑定，并保存为当前激活会话。
 */
@Component
public class GameEngine {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    private final Map<GameType, GameSession> registry = new ConcurrentHashMap<>();
    private final RoomManager roomManager;
    private final Broadcaster broadcaster;

    public GameEngine(List<GameSession> sessions, RoomManager roomManager, Broadcaster broadcaster) {
        this.roomManager = roomManager;
        this.broadcaster = broadcaster;
        for (GameSession s : sessions) {
            registry.put(s.getType(), s);
            log.info("[游戏引擎] 已注册游戏 {} → {}", s.getType(), s.getClass().getSimpleName());
        }
    }

    public synchronized void startGame(GameType type) {
        GameSession session = registry.get(type);
        if (session == null) {
            throw new IllegalArgumentException("未找到对应的游戏会话: " + type);
        }
        stopCurrent();
        room().setGameType(type);
        room().setGameSession(session);
        session.start(room(), broadcaster);
        log.info("[游戏引擎] 启动游戏 {}", type);
    }

    public synchronized void dispatchAction(Player player, Map<String, Object> payload) {
        GameSession session = room().getGameSession();
        if (session == null) {
            log.warn("[游戏引擎] 动作被忽略 - 无活跃游戏");
            return;
        }
        session.handleAction(player, payload);

        // If it's DrawAndGuess, we might want to handle the watchdog check differently
        // but usually, GameEngine should have a periodic tick.
    }

    /**
     * Periodic tick called by a scheduler to handle game-specific time-outs or logic.
     */
    @Scheduled(fixedRate = 1000)
    public synchronized void tick() {
        GameSession session = room().getGameSession();
        if (session == null) return;

        if (session instanceof DrawAndGuessGame) {
            ((DrawAndGuessGame) session).watchdog();
        } else if (session instanceof TetrisGame) {
            ((TetrisGame) session).watchdog();
        } else if (session instanceof SnakeGame) {
            ((SnakeGame) session).watchdog();
        } else if (session instanceof FlappyBirdGame) {
            ((FlappyBirdGame) session).watchdog();
        } else if (session instanceof BreakoutGame) {
            ((BreakoutGame) session).watchdog();
        } else if (session instanceof TankGame) {
            ((TankGame) session).watchdog();
        } else if (session instanceof BombermanGame) {
            ((BombermanGame) session).watchdog();
        }
        // Other games with watchdog/tick logic will be added here.
    }

    public synchronized void stopCurrent() {
        GameSession s = room().getGameSession();
        if (s != null) {
            s.stop();
            log.info("[游戏引擎] 停止游戏 {}", s.getType());
        }
        room().setGameSession(null);
        room().setGameType(null);
    }

    public boolean hasGame(GameType type) {
        return registry.containsKey(type);
    }

    public Map<GameType, GameSession> registeredGames() {
        return Map.copyOf(registry);
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
