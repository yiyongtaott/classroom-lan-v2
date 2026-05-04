package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FlyingChessGame implements GameSession {
    private Room room;
    private Broadcaster broadcaster;
    private boolean gameOver = false;
    private Map<String, Integer> positions = new HashMap<>();

    @Override
    public GameType getType() { return GameType.FLYING_CHESS; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.gameOver = false;
        positions.clear();
        for (Player p : room.getPlayers()) {
            positions.put(p.getId(), 0);
        }
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.get("action");
        if ("ROLL".equals(action)) {
            int dice = ThreadLocalRandom.current().nextInt(1, 7);
            int pos = positions.getOrDefault(player.getId(), 0);
            pos = Math.min(pos + dice, 100);
            positions.put(player.getId(), pos);
            if (pos >= 100) gameOver = true;
            broadcastState("UPDATE");
        }
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("positions", positions);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() {}
}
