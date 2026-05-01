package org.lanclassroom.net.ws;

import org.lanclassroom.net.service.ChatHistoryService;
import org.lanclassroom.net.service.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 聊天 STOMP 控制器。
 * 客户端 send /app/chat → 写入历史 + 广播到 /topic/chat。
 */
@Controller
public class ChatController {

    private final SimpMessagingTemplate messaging;
    private final ChatHistoryService history;

    public ChatController(SimpMessagingTemplate messaging, ChatHistoryService history) {
        this.messaging = messaging;
        this.history = history;
    }

    @MessageMapping("/chat")
    public void onChat(@Payload ChatMessage incoming) {
        if (incoming.getTimestamp() == null) {
            incoming.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (incoming.getSender() == null || incoming.getSender().isBlank()) {
            incoming.setSender("匿名");
        }
        history.append(incoming);
        messaging.convertAndSend(WebSocketConfig.TOPIC_CHAT, incoming);
    }
}
