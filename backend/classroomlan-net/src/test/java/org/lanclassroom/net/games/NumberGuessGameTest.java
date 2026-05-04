package org.lanclassroom.net.games;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NumberGuessGame 状态分发测试 - 模拟 STOMP 广播链路，
 * 断言每个动作都通过 Broadcaster 发出对应阶段事件。
 */
class NumberGuessGameTest {

    private NumberGuessGame game;
    private Room room;
    private List<Map<String, Object>> broadcasts;
    private Broadcaster broadcaster;

    @BeforeEach
    void setUp() {
        game = new NumberGuessGame();
        room = new Room();
        room.addPlayer(new Player("Alice").setId("p1"));
        broadcasts = new ArrayList<>();
        broadcaster = state -> broadcasts.add(asMap(state));
    }

    @Test
    void start_emitsStartedEnvelopeOnTopicGameState() {
        game.start(room, broadcaster);

        assertEquals(1, broadcasts.size());
        Map<String, Object> envelope = broadcasts.get(0);
        assertEquals(GameType.NUMBER_GUESS.name(), envelope.get("gameType"));
        assertEquals("STARTED", envelope.get("stage"));
        assertEquals(1, envelope.get("playerCount"));
    }

    @Test
    void guess_belowTarget_emitsLow() {
        game.start(room, broadcaster);
        broadcasts.clear();
        // force a known target
        forceTarget(50);

        Player p = room.findById("p1").orElseThrow();
        game.handleAction(p, Map.of("action", "GUESS", "value", 10));

        assertEquals(1, broadcasts.size());
        assertEquals("LOW", broadcasts.get(0).get("stage"));
    }

    @Test
    void guess_aboveTarget_emitsHigh() {
        game.start(room, broadcaster);
        broadcasts.clear();
        forceTarget(50);

        game.handleAction(room.findById("p1").orElseThrow(),
                Map.of("action", "GUESS", "value", 90));

        assertEquals("HIGH", broadcasts.get(0).get("stage"));
    }

    @Test
    void guess_correct_emitsWinAndStopsRunning() {
        game.start(room, broadcaster);
        broadcasts.clear();
        forceTarget(42);

        game.handleAction(room.findById("p1").orElseThrow(),
                Map.of("action", "GUESS", "value", 42));

        assertEquals("WIN", broadcasts.get(0).get("stage"));
        assertFalse(game.isRunning());
    }

    @Test
    void afterStop_actionsAreIgnored() {
        game.start(room, broadcaster);
        game.stop();
        broadcasts.clear();

        game.handleAction(room.findById("p1").orElseThrow(),
                Map.of("action", "GUESS", "value", 1));

        assertTrue(broadcasts.isEmpty(), "no broadcasts after stop");
    }

    @Test
    void guess_acceptsStringNumber() {
        game.start(room, broadcaster);
        broadcasts.clear();
        forceTarget(50);

        game.handleAction(room.findById("p1").orElseThrow(),
                Map.of("action", "GUESS", "value", "50"));

        assertEquals("WIN", broadcasts.get(0).get("stage"));
    }

    @Test
    void invalidValue_emitsInvalid() {
        game.start(room, broadcaster);
        broadcasts.clear();

        game.handleAction(room.findById("p1").orElseThrow(),
                Map.of("action", "GUESS", "value", "not-a-number"));

        assertEquals("INVALID", broadcasts.get(0).get("stage"));
    }

    /* --- helpers --- */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** Reflectively pin the target so guessing logic is deterministic. */
    private void forceTarget(int target) {
        try {
            var field = NumberGuessGame.class.getDeclaredField("target");
            field.setAccessible(true);
            field.setInt(game, target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
