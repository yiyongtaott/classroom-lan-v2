package org.lanclassroom.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTest {

    @Test
    void newPlayer_isAdded_andFindable() {
        Room room = new Room();
        Player alice = new Player("Alice");

        room.addPlayer(alice);

        assertEquals(1, room.getPlayers().size());
        assertTrue(room.findById(alice.getId()).isPresent());
    }

    @Test
    void removePlayer_removesByExactId() {
        Room room = new Room();
        Player alice = new Player("Alice");
        room.addPlayer(alice);

        boolean removed = room.removePlayerById(alice.getId());

        assertTrue(removed);
        assertEquals(0, room.getPlayers().size());
    }

    @Test
    void snapshot_capturesState() {
        Room room = new Room();
        room.setHostNodeId("node-1");
        room.setGameType(GameType.NUMBER_GUESS);
        room.addPlayer(new Player("Bob"));

        RoomSnapshot snap = room.snapshot();

        assertNotNull(snap);
        assertEquals("node-1", snap.getHostNodeId());
        assertEquals(GameType.NUMBER_GUESS, snap.getGameType());
        assertEquals(1, snap.getPlayerCount());
    }
}
