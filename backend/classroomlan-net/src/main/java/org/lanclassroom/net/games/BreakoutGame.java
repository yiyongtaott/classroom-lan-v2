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
public class BreakoutGame implements GameSession {
    private Broadcaster broadcaster;
    private volatile boolean running;
    private boolean gameOver = false;
    private int score = 0;

    // Paddle
    private double paddleX = 250;
    private final int PADDLE_WIDTH = 100;
    private final int PADDLE_HEIGHT = 20;

    // Ball
    private double ballX = 300, ballY = 300;
    private double ballDX = 4, ballDY = -4;
    private final int BALL_SIZE = 10;

    // Bricks
    private final int BRICK_ROWS = 5;
    private final int BRICK_COLS = 8;
    private final int BRICK_WIDTH = 60;
    private final int BRICK_HEIGHT = 20;
    private final List<Brick> bricks = new CopyOnWriteArrayList<>();

    private final int BOARD_WIDTH = 600;
    private final int BOARD_HEIGHT = 400;

    public static class Brick {
        public int r, c;
        public boolean active = true;
        public Brick(int r, int c) { this.r = r; this.c = c; }
    }

    @Override
    public GameType getType() { return GameType.BREAKOUT; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.score = 0;
        this.paddleX = 250;
        this.ballX = 300; this.ballY = 300;
        this.ballDX = 4; this.ballDY = -4;

        bricks.clear();
        for (int r = 0; r < BRICK_ROWS; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                bricks.add(new Brick(r, c));
            }
        }
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        if ("LEFT".equals(action)) {
            paddleX = Math.max(0, paddleX - 20);
        } else if ("RIGHT".equals(action)) {
            paddleX = Math.min(BOARD_WIDTH - PADDLE_WIDTH, paddleX + 20);
        }
    }

    public synchronized void watchdog() {
        if (!running || gameOver) return;

        // Ball physics
        ballX += ballDX;
        ballY += ballDY;

        // Wall collision
        if (ballX <= 0 || ballX >= BOARD_WIDTH - BALL_SIZE) ballDX *= -1;
        if (ballY <= 0) ballDY *= -1;

        // Paddle collision
        if (ballY >= BOARD_HEIGHT - PADDLE_HEIGHT - BALL_SIZE) {
            if (ballX >= paddleX && ballX <= paddleX + PADDLE_WIDTH) {
                ballDY *= -1;
                // Add some variance to bounce based on where it hit the paddle
                double hitPos = (ballX - paddleX) / PADDLE_WIDTH;
                ballDX = (hitPos - 0.5) * 8;
            } else if (ballY >= BOARD_HEIGHT - BALL_SIZE) {
                endGame();
                return;
            }
        }

        // Brick collision
        for (Brick b : bricks) {
            if (!b.active) continue;
            int bx = b.c * (BRICK_WIDTH + 10) + 10;
            int by = b.r * (BRICK_HEIGHT + 10) + 30;
            if (ballX >= bx && ballX <= bx + BRICK_WIDTH && ballY >= by && ballY <= by + BRICK_HEIGHT) {
                b.active = false;
                ballDY *= -1;
                score += 10;
                break;
            }
        }

        if (bricks.stream().noneMatch(b -> b.active)) {
            gameOver = true;
            broadcastState("WIN");
            return;
        }

        broadcastState("TICK");
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
        state.put("ball", new double[]{ballX, ballY});
        state.put("paddleX", paddleX);
        state.put("bricks", bricks);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
