package org.lanclassroom.net.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.net.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebSocket 消息处理器 - 协调聊天、游戏消息的发送与广播
 */
@Component
public class ChatWebSocketHandler {

    @Autowired
    private ChatService chatService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 处理客户端发送的聊天消息
     */
    @MessageMapping("/chat")
    public void handleChat(
        @Payload Map<String, Object> payload,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        // 构造 Player 对象（需从 session 中获取真实 playerId/name）
        String playerName = (String) payload.getOrDefault("sender", "Anonymous");
        String content = (String) payload.get("content");
        Player player = new Player(playerName);

        ChatService.ChatMessage msg = chatService.send(player, content);
        // 广播到房间所有用户
        // 需自行注入 SimpMessagingTemplate（此处略简化）
    }

    /**
     * 处理游戏动作
     */
    @MessageMapping("/game.action")
    public void handleGameAction(@Payload Map<String, Object> payload) {
        String action = (String) payload.get("action");
        // 转发给当前激活的 GameSession 处理
    }
}
