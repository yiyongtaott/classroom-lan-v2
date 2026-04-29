package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏引擎 - 管理活动游戏会话，按类型分发消息
 */
@Component
public class GameEngine {

    private final Map<GameType, GameSession> registry = new ConcurrentHashMap<>();

    public void register(GameSession session) {
        registry.put(session.getType(), session);
    }

    public void unregister(GameType type) {
        registry.remove(type);
    }

    /**
     * 开始游戏
     */
    public void start(Room room, GameType type) {
        GameSession session = registry.get(type);
        if (session != null) {
            room.setGameSession(session);
            room.setGameType(type);
            session.start(room);
        }
    }

    /**
     * 分发游戏动作
     */
    public void dispatch(Room room, Player player, String action, Map<String, Object> payload) {
        GameSession session = room.getGameSession();
        if (session != null) {
            session.handleAction(player, Map.of("action", action, "data", payload));
        }
    }

    /**
     * 停止当前游戏
     */
    public void stop(Room room) {
        GameSession session = room.getGameSession();
        if (session != null) {
            session.stop();
            room.setGameSession(null);
            room.setGameType(null);
        }
    }

    /**
     * 获取已注册的游戏
     */
    public boolean hasGame(GameType type) {
        return registry.containsKey(type);
    }
}
