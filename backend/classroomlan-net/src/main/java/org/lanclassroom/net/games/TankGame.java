package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TankGame implements GameSession {
    private Broadcaster broadcaster;
    private volatile boolean running;
    private boolean gameOver = false;
    private final int BOARD_WIDTH = 20;
    private final int BOARD_HEIGHT = 15;

    private final Map<String, Tank> tanks = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final List<Wall> walls = new CopyOnWriteArrayList<>();

    public static class Tank {
        public int r, c, dir; // dir: 0:N, 1:E, 2:S, 3:W
        public int health = 100;
        public String ownerId;
        public Tank(int r, int c, String id) { this.r = r; this.c = c; this.ownerId = id; }
    }

    public static class Bullet {
        public int r, c, dir;
        public String ownerId;
        public Bullet(int r, int c, int dir, String id) { this.r = r; this.c = c; this.dir = dir; this.ownerId = id; }
    }

    public static class Wall {
        public int r, c;
        public Wall(int r, int c) { this.r = r; this.c = c; }
    }

    @Override
    public GameType getType() { return GameType.TANK_TROUBLE; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.tanks.clear();
        this.bullets.clear();
        this.walls.clear();

        int i = 0;
        for (Player p : room.getPlayers()) {
            if (Player.STATUS_ONLINE.equals(p.getStatus())) {
                // Spawn tanks at corners
                int r = (i % 2 == 0) ? 0 : BOARD_HEIGHT - 1;
                int c = (i / 2 % 2 == 0) ? 0 : BOARD_WIDTH - 1;
                tanks.put(p.getId(), new Tank(r, c, p.getId()));
                i++;
            }
        }

        // Random walls
        Random rand = new Random();
        for (int w = 0; w < 30; w++) {
            walls.add(new Wall(rand.nextInt(BOARD_HEIGHT), rand.nextInt(BOARD_WIDTH)));
        }

        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        Tank tank = tanks.get(player.getId());
        if (tank == null) return;

        switch (action) {
            case "UP" -> moveTank(tank, 0, -1);
            case "DOWN" -> moveTank(tank, 0, 1);
            case "LEFT" -> moveTank(tank, -1, 0);
            case "RIGHT" -> moveTank(tank, 1, 0);
            case "FIRE" -> fireBullet(tank);
        }
        broadcastState("UPDATE");
    }

    private void moveTank(Tank tank, int dr, int dc) {
        int nr = tank.r + dr;
        int nc = tank.c + dc;
        if (nr >= 0 && nr < BOARD_HEIGHT && nc >= 0 && nc < BOARD_WIDTH) {
            if (walls.stream().noneMatch(w -> w.r == nr && w.c == nc) &&
                tanks.values().stream().noneMatch(t -> t.r == nr && t.c == nc)) {
                tank.r = nr;
                tank.c = nc;
                // Update direction based on movement
                if (dr == -1) tank.dir = 0;
                else if (dc == 1) tank.dir = 1;
                else if (dr == 1) tank.dir = 2;
                else if (dc == -1) tank.dir = 3;
            }
        }
    }

    private void fireBullet(Tank tank) {
        int br = tank.r;
        int bc = tank.c;
        int bdir = tank.dir;
        bullets.add(new Bullet(br, bc, bdir, tank.ownerId));
    }

    public synchronized void watchdog() {
        if (!running || gameOver) return;

        // Move bullets
        for (Bullet b : bullets) {
            if (b.dir == 0) b.r--;
            else if (b.dir == 1) b.c++;
            else if (b.dir == 2) b.r++;
            else if (b.dir == 3) b.c--;
        }

        // Bullet collisions with walls
        bullets.removeIf(b -> b.r < 0 || b.r >= BOARD_HEIGHT || b.c < 0 || b.c >= BOARD_WIDTH ||
                               walls.stream().anyMatch(w -> w.r == b.r && w.c == b.c));

        // Bullet collisions with tanks
        for (Bullet b : bullets) {
            for (Tank t : tanks.values()) {
                if (!t.ownerId.equals(b.ownerId) && t.r == b.r && t.c == b.c) {
                    t.health -= 20;
                    bullets.remove(b);
                    if (t.health <= 0) {
                        tanks.remove(t.ownerId);
                    }
                }
            }
        }

        if (tanks.size() <= 1) {
            gameOver = true;
        }

        broadcastState("TICK");
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("tanks", tanks);
        state.put("bullets", bullets);
        state.put("walls", walls);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
