package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MinesweeperGame implements GameSession {

    private Room room;
    private Broadcaster broadcaster;

    @Override
    public GameType getType() {
        return GameType.MINESWEEPER;
    }

    private int[][] board;
    private boolean[][] revealed;
    private boolean[][] flagged;
    private int rows = 10, cols = 10, mines = 10;
    private boolean gameOver = false;

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.board = new int[rows][cols];
        this.revealed = new boolean[rows][cols];
        this.flagged = new boolean[rows][cols];
        this.gameOver = false;
        placeMines();
        calculateNeighbors();

        Map<String, Object> start = new HashMap<>();
        start.put("type", getType().name());
        start.put("stage", "START");
        start.put("rows", rows);
        start.put("cols", cols);
        start.put("mineCount", mines);
        broadcaster.broadcast(start);
    }

    private void placeMines() {
        int count = 0;
        while (count < mines) {
            int r = (int) (Math.random() * rows);
            int c = (int) (Math.random() * cols);
            if (board[r][c] != 9) {
                board[r][c] = 9;
                count++;
            }
        }
    }

    private void calculateNeighbors() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] == 9) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc] == 9) count++;
                    }
                }
                board[r][c] = count;
            }
        }
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.getOrDefault("action", "REVEAL");
        int x = (int) payload.getOrDefault("x", -1);
        int y = (int) payload.getOrDefault("y", -1);

        if (x < 0 || x >= rows || y < 0 || y >= cols) return;

        Map<String, Object> response = new HashMap<>();
        response.put("type", getType().name());
        response.put("stage", "MOVE");

        if ("FLAG".equals(action)) {
            flagged[x][y] = !flagged[x][y];
            response.put("flagged", flagged);
            response.put("x", x);
            response.put("y", y);
        } else {
            if (flagged[x][y]) return;
            if (board[x][y] == 9) {
                gameOver = true;
                response.put("msg", "踩雷！游戏结束");
                response.put("gameOver", true);
                response.put("board", board);
                response.put("revealed", revealed);
            } else {
                reveal(x, y);
                if (checkWin()) {
                    gameOver = true;
                    response.put("msg", "胜利！");
                    response.put("gameOver", true);
                    response.put("board", board);
                } else {
                    response.put("revealed", getRevealedState());
                    response.put("cellValue", board[x][y]);
                    response.put("x", x);
                    response.put("y", y);
                }
            }
        }
        broadcaster.broadcast(response);
    }

    private void reveal(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols || revealed[r][c]) return;
        revealed[r][c] = true;
        if (board[r][c] == 0) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    reveal(r + dr, c + dc);
                }
            }
        }
    }

    private boolean checkWin() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] != 9 && !revealed[r][c]) return false;
            }
        }
        return true;
    }

    private boolean[][] getRevealedState() { return revealed; }

    @Override
    public void stop() {
        Map<String, Object> stop = new HashMap<>();
        stop.put("type", getType().name());
        stop.put("stage", "STOP");
        stop.put("msg", "扫雷结束");
        broadcaster.broadcast(stop);
    }
}
