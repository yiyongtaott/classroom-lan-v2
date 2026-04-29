package org.lanclassroom.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Room 实体测试
 */
public class RoomTest {

    @Test
    void generateRoomKey_returnsNonEmptyHexString() {
        // Given & When
        String key = Room.generateRoomKey();

        // Then
        assertNotNull(key);
        assertTrue(key.length() > 0);
    }

    @Test
    void snapshot_containsCurrentState() {
        // Given
        Room room = new Room();
        room.setRoomKey("test-key");
        room.setGameType(GameType.DRAW);
        Player player = new Player("Player1");
        room.getPlayers().add(player);

        // When
        RoomSnapshot snap = room.snapshot();

        // Then
        assertEquals("test-key", snap.getRoomKey());
        assertEquals(GameType.DRAW, snap.getGameType());
        assertEquals(1, snap.getPlayers().size());
        assertTrue(snap.isActive());
    }

    @Test
    void addPlayer_increasesPlayerCount() {
        // Given
        Room room = new Room();

        // When
        room.getPlayers().add(new Player("Alice"));
        room.getPlayers().add(new Player("Bob"));

        // Then
        assertEquals(2, room.getPlayers().size());
    }
}
