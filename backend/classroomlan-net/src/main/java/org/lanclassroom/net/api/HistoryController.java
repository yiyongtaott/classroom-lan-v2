package org.lanclassroom.net.api;

import org.lanclassroom.net.service.ChatHistoryService;
import org.lanclassroom.net.service.ChatMessage;
import org.lanclassroom.net.service.GameHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 历史读取接口 - 客户端进入聊天/游戏页面时拉取历史。
 */
@RestController
@RequestMapping("/api")
public class HistoryController {

    private final ChatHistoryService chatHistory;
    private final GameHistoryService gameHistory;

    public HistoryController(ChatHistoryService chatHistory, GameHistoryService gameHistory) {
        this.chatHistory = chatHistory;
        this.gameHistory = gameHistory;
    }

    @GetMapping("/chat/history")
    public ResponseEntity<List<ChatMessage>> chat() {
        return ResponseEntity.ok(chatHistory.all());
    }

    @DeleteMapping("/chat/history")
    public ResponseEntity<Void> clearChat() {
        chatHistory.clear();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/game/history")
    public ResponseEntity<List<Map<String, Object>>> game() {
        return ResponseEntity.ok(gameHistory.all());
    }

    @DeleteMapping("/game/history")
    public ResponseEntity<Void> clearGame() {
        gameHistory.clear();
        return ResponseEntity.noContent().build();
    }
}
