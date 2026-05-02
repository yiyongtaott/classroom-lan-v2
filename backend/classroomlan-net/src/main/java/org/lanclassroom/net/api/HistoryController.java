package org.lanclassroom.net.api;

import org.lanclassroom.net.service.ChatHistoryService;
import org.lanclassroom.net.service.ChatMessage;
import org.lanclassroom.net.service.GameHistoryService;
import org.lanclassroom.net.service.GameInvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 历史读取接口 + 当前游戏邀请查询。
 */
@RestController
@RequestMapping("/api")
public class HistoryController {

    private final ChatHistoryService chatHistory;
    private final GameHistoryService gameHistory;
    private final GameInvitationService invitations;

    public HistoryController(ChatHistoryService chatHistory,
                             GameHistoryService gameHistory,
                             GameInvitationService invitations) {
        this.chatHistory = chatHistory;
        this.gameHistory = gameHistory;
        this.invitations = invitations;
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

    @GetMapping("/game/invitation")
    public ResponseEntity<GameInvitationService.Invitation> invitation() {
        GameInvitationService.Invitation inv = invitations.getCurrent();
        return inv == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(inv);
    }
}
