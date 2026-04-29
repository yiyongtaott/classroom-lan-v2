package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 聊天服务 - 管理聊天消息队列和广播逻辑
 */
@Service
public class ChatService {

    private static final int MAX_HISTORY = 100;

    private final Queue<ChatMessage> history = new ConcurrentLinkedQueue<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 发送聊天消息
     */
    public ChatMessage send(Player sender, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(sender.getId());
        msg.setSenderName(sender.getName());
        msg.setContent(content);
        msg.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 保留历史
        if (history.size() >= MAX_HISTORY) {
            history.poll();
        }
        history.offer(msg);

        // 广播到房间频道（所有订阅者）
        messagingTemplate.convertAndSend("/topic/chat", msg);

        return msg;
    }

    /**
     * 获取最近聊天历史
     */
    public Queue<ChatMessage> getHistory() {
        return history;
    }

    /**
     * 清空历史（Host 切换时）
     */
    public void clear() {
        history.clear();
    }

    /**
     * 聊天消息 DTO
     */
    public static class ChatMessage {
        private String senderId;
        private String senderName;
        private String content;
        private String timestamp;

        // Getters & Setters
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }

        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
}
