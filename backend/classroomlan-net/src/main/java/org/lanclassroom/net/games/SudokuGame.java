package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SudokuGame implements GameSession {
    private Broadcaster broadcaster;
    private volatile boolean running;
    private int[][] board = new int[9][9];
    private int[][] solution = new int[9][9];
    private int[][] revealed = new int[9][9];
    private int score = 0;
    private boolean gameOver = false;

    @Override
    public GameType getType() { return GameType.SUDOKU; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.running = true;
        this.score = 0;
        this.gameOver = false;
        generateSudoku();
        broadcastState("START");
    }

    private void generateSudoku() {
        // Fill solution
        fillSudoku(solution);

        // Create revealed board (puzzle)
        revealed = new int[9][9];
        Random rand = new Random();
        int holes = 40 + rand.nextInt(20); // 40-60 holes

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                revealed[i][j] = solution[i][j];
            }
        }

        int count = 0;
        while (count < holes) {
            int r = rand.nextInt(9);
            int c = rand.nextInt(9);
            if (revealed[r][c] != 0) {
                revealed[r][c] = 0;
                count++;
            }
        }

        // Current board starts as revealed
        this.board = new int[9][9];
        for (int i = 0; i < 9; i++) {
            System.arraycopy(revealed[i], 0, board[i], 0, 9);
        }
    }

    private boolean fillSudoku(int[][] b) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (b[r][c] == 0) {
                    List<Integer> nums = Arrays.asList(1,2,3,4,5,6,7,8,9);
                    Collections.shuffle(nums);
                    for (int n : nums) {
                        if (isValid(b, r, c, n)) {
                            b[r][c] = n;
                            if (fillSudoku(b)) return true;
                            b[r][c] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isValid(int[][] b, int r, int c, int n) {
        for (int i = 0; i < 9; i++) {
            if (b[r][i] == n || b[i][c] == n) return false;
        }
        int startR = (r / 3) * 3;
        int startC = (c / 3) * 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (b[startR + i][startC + j] == n) return false;
            }
        }
        return true;
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;

        int r = (int) payload.getOrDefault("r", -1);
        int c = (int) payload.getOrDefault("c", -1);
        int val = (int) payload.getOrDefault("val", -1);

        if (r < 0 || r >= 9 || c < 0 || c >= 9 || val < 1 || val > 9) return;
        if (revealed[r][c] != 0) return; // Cannot change pre-filled cells

        if (solution[r][c] == val) {
            board[r][c] = val;
            score++;
            if (checkWin()) {
                gameOver = true;
                broadcastState("WIN");
            } else {
                broadcastState("UPDATE");
            }
        } else {
            // Wrong answer - maybe just broadcast state to let frontend handle a "shake"
            broadcastState("WRONG");
        }
    }

    private boolean checkWin() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] == 0) return false;
            }
        }
        return true;
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("board", board);
        state.put("initial", revealed);
        state.put("score", score);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() { this.running = false; }
}
