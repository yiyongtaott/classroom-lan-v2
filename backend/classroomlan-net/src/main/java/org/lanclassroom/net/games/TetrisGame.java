package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TetrisGame implements GameSession {

    private Room room;
    private Broadcaster broadcaster;
    private int[][] board = new int[20][10];
    private Piece currentPiece;
    private boolean gameOver = false;
    private int score = 0;

    @Override
    public GameType getType() {
        return GameType.TETRIS;
    }

    // Tetromino definitions: 0=empty, 1-7=different shapes
    private static final int[][][] SHAPES = {
        {{1, 1, 1, 1}}, // I
        {{1, 0, 0}, {1, 1, 1}}, // J
        {{0, 0, 1}, {1, 1, 1}}, // L
        {{1, 1}, {1, 1}}, // O
        {{0, 1, 1}, {1, 1, 0}}, // S
        {{0, 1, 0}, {1, 1, 1}}, // T
        {{1, 1, 0}, {0, 1, 1}}  // Z
    };

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.board = new int[20][10];
        this.gameOver = false;
        this.score = 0;
        spawnPiece();
        broadcastState("START");
    }

    private void spawnPiece() {
        int type = new Random().nextInt(SHAPES.length);
        int[][] shape = SHAPES[type];
        currentPiece = new Piece(shape, 0, (10 - shape[0].length) / 2, type + 1);
        if (checkCollision(currentPiece.r, currentPiece.c, currentPiece.shape)) {
            gameOver = true;
        }
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        switch (action) {
            case "LEFT" -> movePiece(0, -1);
            case "RIGHT" -> movePiece(0, 1);
            case "ROTATE" -> rotatePiece();
            case "DOWN" -> movePiece(1, 0);
            case "HARD_DROP" -> hardDrop();
        }
        broadcastState("MOVE");
    }

    private void movePiece(int dr, int dc) {
        if (currentPiece == null || gameOver) return;
        if (!checkCollision(currentPiece.r + dr, currentPiece.c + dc, currentPiece.shape)) {
            currentPiece.r += dr;
            currentPiece.c += dc;
        } else if (dr == 1) {
            merge();
            clearLines();
            spawnPiece();
        }
    }

    private void hardDrop() {
        if (currentPiece == null || gameOver) return;
        while (!checkCollision(currentPiece.r + 1, currentPiece.c, currentPiece.shape)) {
            currentPiece.r++;
        }
        merge();
        clearLines();
        spawnPiece();
    }

    private void rotatePiece() {
        if (currentPiece == null || gameOver) return;
        int[][] shape = currentPiece.shape;
        int r = shape.length;
        int c = shape[0].length;
        int[][] newShape = new int[c][r];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                newShape[j][r - 1 - i] = shape[i][j];
            }
        }
        if (!checkCollision(currentPiece.r, currentPiece.c, newShape)) {
            currentPiece.shape = newShape;
        }
    }

    private boolean checkCollision(int r, int c, int[][] shape) {
        for (int i = 0; i < shape.length; i++) {
            for (int j = 0; j < shape[0].length; j++) {
                if (shape[i][j] != 0) {
                    int nr = r + i;
                    int nc = c + j;
                    if (nr < 0 || nr >= 20 || nc < 0 || nc >= 10 || (nr >= 0 && board[nr][nc] != 0)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void merge() {
        if (currentPiece == null) return;
        for (int i = 0; i < currentPiece.shape.length; i++) {
            for (int j = 0; j < currentPiece.shape[0].length; j++) {
                if (currentPiece.shape[i][j] != 0) {
                    int nr = currentPiece.r + i;
                    int nc = currentPiece.c + j;
                    if (nr >= 0 && nr < 20) {
                        board[nr][nc] = currentPiece.type;
                    }
                }
            }
        }
    }

    private void clearLines() {
        int linesCleared = 0;
        for (int i = 19; i >= 0; i--) {
            boolean full = true;
            for (int j = 0; j < 10; j++) {
                if (board[i][j] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                linesCleared++;
                for (int k = i; k > 0; k--) {
                    board[k] = board[k - 1].clone();
                }
                board[0] = new int[10];
                i++;
            }
        }
        score += linesCleared * 100;
    }

    public synchronized void watchdog() {
        if (gameOver || currentPiece == null) return;
        movePiece(1, 0);
        broadcastState("TICK");
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("board", board);
        state.put("piece", currentPiece);
        state.put("score", score);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() {
        Map<String, Object> stop = new HashMap<>();
        stop.put("type", getType().name());
        stop.put("stage", "STOP");
        stop.put("msg", "俄罗斯方块结束");
        broadcaster.broadcast(stop);
    }

    public static class Piece {
        public int[][] shape;
        public int r, c, type;
        Piece(int[][] s, int r, int c, int type) {
            this.shape = s; this.r = r; this.c = c; this.type = type;
        }
    }
}
