package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TankTroubleGame implements GameSession {
    private Room room;
    private Broadcaster broadcaster;
    private boolean gameOver = false;
    private Map<String, Tank> tanks = new HashMap<>();

    @Override
    public GameType getType() { return GameType.TANK_TROUBLE; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.gameOver = false;
        tanks.clear();
        for (Player p : room.getPlayers()) {
            tanks.put(p.getId(), new Tank(0, 0, 0));
        }
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.get("action");
        if ("MOVE".equals(action)) {
            Tank t = tanks.get(player.getId());
            if (t != null) {
                t.x += (int) payload.get("dx");
                t.y += (int) payload.get("dy");
            }
            broadcastState("UPDATE");
        }
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("tanks", tanks);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() {}

    public static class Tank {
        public int x, y, angle;
        Tank(int x, int y, int a) { this.x=x; this.y=y; this.angle=a; }
    }
}
