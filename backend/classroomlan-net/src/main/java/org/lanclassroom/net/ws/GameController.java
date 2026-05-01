package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.service.GameEngine;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 游戏 STOMP 控制器。
 *
 * 客户端 → /app/game.start  {type, playerId}     启动指定游戏
 * 客户端 → /app/game.action {playerId, ...}      游戏动作（具体 schema 由游戏决定）
 * 客户端 → /app/game.stop                        停止当前游戏
 *
 * 游戏状态通过 Broadcaster 广播到 /topic/game.state（由 GameSession 实现负责）。
 */
@Controller
public class GameController {

    private final GameEngine engine;
    private final Room room;

    public GameController(GameEngine engine, Room room) {
        this.engine = engine;
        this.room = room;
    }

    @MessageMapping("/game.start")
    public void onStart(@Payload Map<String, Object> payload) {
        String typeName = String.valueOf(payload.get("type"));
        engine.startGame(GameType.valueOf(typeName));
    }

    @MessageMapping("/game.action")
    public void onAction(@Payload Map<String, Object> payload) {
        String playerId = String.valueOf(payload.getOrDefault("playerId", ""));
        Player player = room.findById(playerId).orElseGet(() -> new Player("anonymous").setId(playerId));
        engine.dispatchAction(player, payload);
    }

    @MessageMapping("/game.stop")
    public void onStop() {
        engine.stopCurrent();
    }
}
