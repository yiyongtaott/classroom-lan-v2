package org.lanclassroom.net.service;

import org.lanclassroom.core.model.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService 单元测试
 */
public class ChatServiceTest {

    @Test
    void sendMessage_createsMessageWithTimestamp() {
        // Given
        Player player = new Player("Alice");
        ChatService service = new ChatService();

        // When
        ChatService.ChatMessage msg = service.send(player, "Hello");

        // Then
        assertEquals("Alice", msg.getSenderName());
        assertEquals("Hello", msg.getContent());
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void sendMessage_limitExceeded_removesOldest() {
        // Given
        Player player = new Player("Bob");
        ChatService service = new ChatService();

        // When - send 105 messages (exceeds MAX_HISTORY=100)
        for (int i = 0; i < 105; i++) {
            service.send(player, "msg-" + i);
        }

        // Then
        assertEquals(100, service.getHistory().size());
    }

    @Test
    void clear_emptiesHistory() {
        // Given
        Player player = new Player("Charlie");
        ChatService service = new ChatService();
        service.send(player, "test");

        // When
        service.clear();

        // Then
        assertTrue(service.getHistory().isEmpty());
    }
}
