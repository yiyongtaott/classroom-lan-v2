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
public class SnakeGame implements GameSession {
    private Broadcaster broadcaster;
    private Room room;
    private volatile boolean running;
    private boolean gameOver = false;
    private int score = 0;

    private final List<int[]> snake = new CopyOnWriteArrayList<>();
    private int[] food = new int[2];
    private int[] direction = {0, 1}; // Initial direction: Right
    private final int ROWS = 20;
    private final int COLS = 20;

    @Override
    public GameType getType() { return GameType.SNAKE; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.score = 0;
        this.snake.clear();
        this.snake.add(new int[]{10, 10});
        this.snake.add(new int[]{10, 9});
        this.snake.add(new int[]{10, 8});
        this.direction = new int[]{0, 1};
        spawnFood();
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        switch (action) {
            case "UP" -> setDirection(0, -1);
            case "DOWN" -> setDirection(0, 1);
            case "LEFT" -> setDirection(-1, 0);
            case "RIGHT" -> setDirection(1, 0);
        }
    }

    private synchronized void setDirection(int dr, int dc) {
        // Prevent 180-degree turns
        if (direction[0] == -dr && direction[1] == -dc) return;
        this.direction = new int[]{dr, dc};
    }

    public synchronized void watchdog() {
        if (!running || gameOver) return;

        // Move snake
        int[] head = snake.get(0);
        int newR = head[0] + direction[0];
        int newC = head[1] + direction[1];

        // Collision with walls
        if (newR < 0 || newR >= ROWS || newC < 0 || newC >= COLS) {
            endGame();
            return;
        }

        // Collision with self
        for (int[] p : snake) {
            if (p[0] == newR && p[1] == newC) {
                endGame();
                return;
            }
        }

        // Move head
        int[] newHead = new int[]{newR, newC};
        snake.add(0, newHead);

        // Check food
        if (newR == food[0] && newC == food[1]) {
            score++;
            spawnFood();
        } else {
            snake.remove(snake.size() - 1);
        }

        broadcastState("TICK");
    }

    private void spawnFood() {
        Random rand = new Random();
        while (true) {
            int r = rand.nextInt(ROWS);
            int c = rand.nextInt(COLS);
            boolean collision = false;
            for (int[] p : snake) {
                if (p[0] == r && p[1] == c) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                food = new int[]{r, c};
                break;
            }
        }
    }

    private void endGame() {
        this.gameOver = true;
        broadcastState("GAME_OVER");
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("score", score);
        state.put("snake", snake);
        state.put("food", food);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
