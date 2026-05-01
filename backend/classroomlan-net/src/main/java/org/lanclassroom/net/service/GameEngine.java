package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Room room;
    private final Broadcaster broadcaster;

    public GameEngine(List<GameSession> sessions, Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        for (GameSession s : sessions) {
            registry.put(s.getType(), s);
            log.info("[GameEngine] registered {} → {}", s.getType(), s.getClass().getSimpleName());
        }
    }

    public synchronized void startGame(GameType type) {
        GameSession session = registry.get(type);
        if (session == null) {
            throw new IllegalArgumentException("No GameSession registered for: " + type);
        }
        stopCurrent();
        room.setGameType(type);
        room.setGameSession(session);
        session.start(room, broadcaster);
        log.info("[GameEngine] started {}", type);
    }

    public synchronized void dispatchAction(Player player, Map<String, Object> payload) {
        GameSession session = room.getGameSession();
        if (session == null) {
            log.warn("[GameEngine] action ignored - no active game");
            return;
        }
        session.handleAction(player, payload);
    }

    public synchronized void stopCurrent() {
        GameSession s = room.getGameSession();
        if (s != null) {
            s.stop();
            log.info("[GameEngine] stopped {}", s.getType());
        }
        room.setGameSession(null);
        room.setGameType(null);
    }

    public boolean hasGame(GameType type) {
        return registry.containsKey(type);
    }

    public Map<GameType, GameSession> registeredGames() {
        return Map.copyOf(registry);
    }
}
