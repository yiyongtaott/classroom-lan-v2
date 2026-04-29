package org.lanclassroom.net.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.GameSession;
import org.lanclassroom.net.service.GameEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 游戏 WebSocket 处理器 - 处理游戏动作和状态推送
 */
@Component
public class GameWebSocketHandler {

    @Autowired
    private GameEngine gameEngine;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 处理游戏动作
     * Client → /app/game.action { "action": "START_DRAW", "payload": { "word": "苹果" } }
     */
    @MessageMapping("/game.action")
    public void handleGameAction(@Payload Map<String, Object> payload) {
        String action = (String) payload.get("action");
        Map<String, Object> data = (Map<String, Object>) payload.get("payload");

        // TODO: 从 session 获取当前 Room
        // Room room = ...;

        // 转发给游戏引擎处理
        // gameEngine.dispatch(room, action, data);
        System.out.println("[Game] Action: " + action + " | Data: " + data);
    }

    /**
     * 发送游戏状态更新到客户端
     * 使用 /topic/game.state 广播
     */
    public void broadcastState(Room room, GameSession session, Object state) {
        // 通过 SimpMessagingTemplate 发送到 /topic/game.state
        // message: { "gameType": "DRAW", "state": { ... } }
    }
}
