package org.lanclassroom.net.ws;

import org.lanclassroom.net.service.ConnectionTracker;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 玩家在线状态 STOMP 控制器。
 * 客户端 connect 上 /ws 后必须发送 /app/player.online {playerId} 完成绑定。
 */
@Controller
public class PlayerController {

    private final ConnectionTracker tracker;

    public PlayerController(ConnectionTracker tracker) {
        this.tracker = tracker;
    }

    @MessageMapping("/player.online")
    public void online(@Payload Map<String, String> payload,
                       SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String playerId = payload == null ? null : payload.get("playerId");
        tracker.bind(sessionId, playerId);
    }
}
