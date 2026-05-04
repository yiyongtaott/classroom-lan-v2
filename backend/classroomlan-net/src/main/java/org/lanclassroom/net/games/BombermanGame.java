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
public class BombermanGame implements GameSession {
    private Broadcaster broadcaster;
    private volatile boolean running;
    private boolean gameOver = false;
    private final int ROWS = 15;
    private final int COLS = 15;

    private final Map<String, PlayerPos> players = new ConcurrentHashMap<>();
    private final List<Bomb> bombs = new CopyOnWriteArrayList<>();
    private final int[][] grid = new int[15][15]; // 0: empty, 1: wall, 2: destructible

    public static class PlayerPos {
        public int r, c;
        public int health = 100;
        public PlayerPos(int r, int c) { this.r = r; this.c = c; }
    }

    public static class Bomb {
        public int r, c, timer;
        public String ownerId;
        public Bomb(int r, int c, String ownerId) {
            this.r = r; this.c = c; this.ownerId = ownerId;
            this.timer = 3; // 3 seconds to explode
        }
    }

    @Override
    public GameType getType() { return GameType.BOMBERMAN; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.players.clear();
        this.bombs.clear();

        // Initialize grid
        Random rand = new Random();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1) grid[r][c] = 1;
                else if (rand.nextInt(10) < 3) grid[r][c] = 2;
                else grid[r][c] = 0;
            }
        }

        for (Player p : room.getPlayers()) {
            if (Player.STATUS_ONLINE.equals(p.getStatus())) {
                players.put(p.getId(), new PlayerPos(0, 0));
            }
        }

        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        PlayerPos pos = players.get(player.getId());
        if (pos == null) return;

        switch (action) {
            case "UP" -> move(pos, 0, -1);
            case "DOWN" -> move(pos, 0, 1);
            case "LEFT" -> move(pos, -1, 0);
            case "RIGHT" -> move(pos, 1, 0);
            case "PLACE_BOMB" -> {
                bombs.add(new Bomb(pos.r, pos.c, player.getId()));
            }
        }
        broadcastState("UPDATE");
    }

    private void move(PlayerPos pos, int dr, int dc) {
        int nr = pos.r + dr;
        int nc = pos.c + dc;
        if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && grid[nr][nc] == 0) {
            pos.r = nr;
            pos.c = nc;
        }
    }

    public synchronized void watchdog() {
        if (!running || gameOver) return;

        // Update bombs
        for (Bomb b : bombs) {
            b.timer--;
            if (b.timer <= 0) {
                explode(b);
            }
        }
        bombs.removeIf(b -> b.timer <= 0);

        broadcastState("TICK");
    }

    private void explode(Bomb b) {
        // Cross explosion
        int[][] dirs = {{0,0}, {0,1}, {0,-1}, {1,0}, {-1,0}};
        for (int[] d : dirs) {
            int nr = b.r + d[0];
            int nc = b.c + d[1];
            if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS) {
                if (grid[nr][nc] == 2) {
                    grid[nr][nc] = 0;
                } else if (grid[nr][nc] == 0) {
                    // Check for players
                    for (Map.Entry<String, PlayerPos> entry : players.entrySet()) {
                        PlayerPos p = entry.getValue();
                        if (p.r == nr && p.c == nc) {
                            p.health -= 50;
                            if (p.health <= 0) {
                                players.remove(entry.getKey());
                            }
                        }
                    }
                }
            }
        }

        if (players.size() <= 1) {
            gameOver = true;
        }
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("players", players);
        state.put("bombs", bombs);
        state.put("grid", grid);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
