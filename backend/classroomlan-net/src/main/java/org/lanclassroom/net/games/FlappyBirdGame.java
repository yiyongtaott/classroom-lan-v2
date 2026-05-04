package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FlappyBirdGame implements GameSession {
    private Broadcaster broadcaster;
    private volatile boolean running;
    private boolean gameOver = false;
    private int score = 0;

    // Bird State
    private double birdY = 200;
    private double velocity = 0;
    private final double GRAVITY = 0.6;
    private final double JUMP_STRENGTH = -8;

    // Pipes
    private final List<Pipe> pipes = new CopyOnWriteArrayList<>();
    private final int PIPE_WIDTH = 50;
    private final int PIPE_GAP = 150;
    private final int BOARD_WIDTH = 600;
    private final int BOARD_HEIGHT = 400;
    private long lastPipeSpawnTs;

    public static class Pipe {
        public double x;
        public int topHeight;
        public Pipe(double x, int topHeight) { this.x = x; this.topHeight = topHeight; }
    }

    @Override
    public GameType getType() { return GameType.FLAPPY_BIRD; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.score = 0;
        this.birdY = 200;
        this.velocity = 0;
        this.pipes.clear();
        this.lastPipeSpawnTs = System.currentTimeMillis();
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if ("FLAP".equals(action)) {
            velocity = JUMP_STRENGTH;
        }
    }

    public synchronized void watchdog() {
        if (!running || gameOver) return;

        // Physics: Gravity
        velocity += GRAVITY;
        birdY += velocity;

        // Wall Collision
        if (birdY < 0 || birdY > BOARD_HEIGHT) {
            endGame();
            return;
        }

        // Pipe movement and collision
        long now = System.currentTimeMillis();
        if (now - lastPipeSpawnTs > 2000) {
            spawnPipe();
            lastPipeSpawnTs = now;
        }

        for (Pipe p : pipes) {
            p.x -= 5;

            // Collision detection
            if (p.x < 50 + PIPE_WIDTH && p.x + PIPE_WIDTH > 50) {
                if (birdY < p.topHeight || birdY > p.topHeight + PIPE_GAP) {
                    endGame();
                    return;
                }
            }

            // Scoring
            if (p.x == 50) {
                score++;
            }
        }

        pipes.removeIf(p -> p.x < -PIPE_WIDTH);

        broadcastState("TICK");
    }

    private void spawnPipe() {
        int minHeight = 50;
        int maxHeight = BOARD_HEIGHT - PIPE_GAP - 50;
        int topHeight = minHeight + new Random().nextInt(maxHeight - minHeight + 1);
        pipes.add(new Pipe(BOARD_WIDTH, topHeight));
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
        state.put("birdY", birdY);
        state.put("pipes", pipes);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
