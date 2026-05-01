package org.lanclassroom.net.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 聊天消息 DTO。客户端发送时只填 sender + content；
 * 服务端补 timestamp 后广播。
 */
public class ChatMessage {

    private String sender;
    private String content;
    private String timestamp;

    public ChatMessage() {}

    public ChatMessage(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
