package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.service.GameEngine;
import org.lanclassroom.net.service.GameInvitationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 游戏 STOMP 控制器。
 *
 * /app/game.start              {type, playerId}    发起邀请，等待全员响应
 * /app/game.invitation.respond {playerId, response} 接受 / 拒绝 / 强制进入
 * /app/game.action             {playerId, ...}     游戏动作
 * /app/game.stop                                    停止当前游戏
 */
@Controller
public class GameController {

    private final GameEngine engine;
    private final GameInvitationService invitations;
    private final Room room;

    public GameController(GameEngine engine, GameInvitationService invitations, Room room) {
        this.engine = engine;
        this.invitations = invitations;
        this.room = room;
    }

    @MessageMapping("/game.start")
    public void onStart(@Payload Map<String, Object> payload) {
        String typeName = String.valueOf(payload.get("type"));
        String initiator = (String) payload.getOrDefault("playerId", null);
        invitations.start(GameType.valueOf(typeName), initiator);
    }

    @MessageMapping("/game.invitation.respond")
    public void onInvitationRespond(@Payload Map<String, String> payload) {
        invitations.respond(payload.get("playerId"), payload.get("response"));
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
        invitations.cancel();
    }
}
