package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.net.service.GameEngine;
import org.lanclassroom.net.service.GameInvitationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏 STOMP 控制器。
 *
 * /app/game.start                 {type, playerId}     发起邀请
 * /app/game.invitation.respond    {playerId, response} 接受 / 拒绝 / 强制进入
 * /app/game.action                {playerId, ...}      游戏动作（猜数字 / 你画我猜共用）
 * /app/game.stop                                       停止当前游戏
 *
 * 你画我猜专用便捷入口（也可以走 /app/game.action 携带 action 字段）：
 * /app/game.draw.select           {index}
 * /app/game.draw.stroke           {points, color, lineWidth, tool}
 * /app/game.draw.guess            {guess}
 * /app/game.draw.clear
 */
@Controller
public class GameController {

    private final GameEngine engine;
    private final GameInvitationService invitations;
    private final RoomManager roomManager;
    private final ClientSessionRegistry sessions;
    private final org.lanclassroom.net.service.ConnectionTracker tracker;

    public GameController(GameEngine engine, GameInvitationService invitations, RoomManager roomManager,
                          ClientSessionRegistry sessions,
                          org.lanclassroom.net.service.ConnectionTracker tracker) {
        this.engine = engine;
        this.invitations = invitations;
        this.roomManager = roomManager;
        this.sessions = sessions;
        this.tracker = tracker;
    }

    @MessageMapping("/game.start")
    public void onStart(@Payload Map<String, Object> payload) {
        String typeName = String.valueOf(payload.get("type"));
        String initiator = (String) payload.getOrDefault("playerId", null);
        String target = (String) payload.getOrDefault("targetId", null);
        invitations.start(GameType.valueOf(typeName), initiator, target);
    }

    @MessageMapping("/game.invitation.respond")
    public void onInvitationRespond(@Payload Map<String, String> payload) {
        invitations.respond(payload.get("playerId"), payload.get("response"));
    }

    @MessageMapping("/game.action")
    public void onAction(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        Player player = playerOf(payload, accessor);
        engine.dispatchAction(player, payload);
    }

    @MessageMapping("/game.stop")
    public void onStop() {
        engine.stopCurrent();
        invitations.cancel();
    }

    @MessageMapping("/game.draw.select")
    public void onDrawSelect(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        Map<String, Object> p = withAction(payload, "SELECT");
        engine.dispatchAction(playerOf(payload, accessor), p);
    }

    @MessageMapping("/game.draw.stroke")
    public void onDrawStroke(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        Map<String, Object> p = withAction(payload, "STROKE");
        engine.dispatchAction(playerOf(payload, accessor), p);
    }

    @MessageMapping("/game.draw.guess")
    public void onDrawGuess(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        Map<String, Object> p = withAction(payload, "GUESS");
        engine.dispatchAction(playerOf(payload, accessor), p);
    }

    @MessageMapping("/game.draw.clear")
    public void onDrawClear(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        Map<String, Object> p = withAction(payload, "CLEAR");
        engine.dispatchAction(playerOf(payload, accessor), p);
    }

    private Player playerOf(Map<String, Object> payload, SimpMessageHeaderAccessor accessor) {
        String pid = payload == null ? null : (String) payload.get("playerId");
        if (pid != null) {
            Player p = room().findById(pid).orElse(null);
            if (p != null) return p;
        }
        String boundId = tracker.getPlayerIdBySession(accessor.getSessionId());
        if (boundId != null) {
            Player p = room().findById(boundId).orElse(null);
            if (p != null) return p;
        }
        // 回退：从 session ip 反查
        String ip = sessions.getIpBySession(accessor.getSessionId());
        if (ip != null) {
            Player p = room().findByIp(ip).orElse(null);
            if (p != null) return p;
        }
        return new Player(pid != null ? pid : null, "anonymous");
    }

    private static Map<String, Object> withAction(Map<String, Object> orig, String action) {
        Map<String, Object> next = orig == null ? new HashMap<>() : new HashMap<>(orig);
        next.put("action", action);
        return next;
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
