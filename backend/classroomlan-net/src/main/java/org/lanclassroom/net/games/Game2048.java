package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Game2048 implements GameSession {

    private Room room;
    private Broadcaster broadcaster;
    private int[][] board = new int[4][4];
    private int score = 0;
    private boolean gameOver = false;

    @Override
    public GameType getType() {
        return GameType.GAME_2048;
    }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.board = new int[4][4];
        this.score = 0;
        this.gameOver = false;
        spawnTile();
        spawnTile();
        broadcastState("START");
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        boolean moved = false;
        switch (action) {
            case "UP" -> moved = move(-1, 0);
            case "DOWN" -> moved = move(1, 0);
            case "LEFT" -> moved = move(0, -1);
            case "RIGHT" -> moved = move(0, 1);
        }

        if (moved) {
            spawnTile();
            if (isGameOver()) gameOver = true;
        }
        broadcastState("MOVE");
    }

    private void spawnTile() {
        List<int[]> emptyCells = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (board[i][j] == 0) emptyCells.add(new int[]{i, j});
            }
        }
        if (!emptyCells.isEmpty()) {
            int[] cell = emptyCells.get(new Random().nextInt(emptyCells.size()));
            board[cell[0]][cell[1]] = Math.random() < 0.9 ? 2 : 4;
        }
    }

    private boolean move(int dr, int dc) {
        boolean moved = false;
        if (dr != 0) { // vertical move
            for (int col = 0; col < 4; col++) {
                List<Integer> column = new ArrayList<>();
                for (int row = 0; row < 4; row++) if (board[row][col] != 0) column.add(board[row][col]);
                if (dr > 0) Collections.reverse(column); // down move

                List<Integer> merged = mergeList(column);
                if (dr > 0) Collections.reverse(merged);

                int r = (dr > 0) ? 3 : 0;
                for (int i = 0; i < 4; i++) {
                    int newVal = (i < merged.size()) ? merged.get(i) : 0;
                    if (board[(dr > 0) ? 3 - i : i][col] != newVal) {
                        board[(dr > 0) ? 3 - i : i][col] = newVal;
                        moved = true;
                    }
                }
            }
        } else { // horizontal move
            for (int row = 0; row < 4; row++) {
                List<Integer> line = new ArrayList<>();
                for (int col = 0; col < 4; col++) if (board[row][col] != 0) line.add(board[row][col]);
                if (dc > 0) Collections.reverse(line);

                List<Integer> merged = mergeList(line);
                if (dc > 0) Collections.reverse(merged);

                for (int i = 0; i < 4; i++) {
                    int newVal = (i < merged.size()) ? merged.get(i) : 0;
                    if (board[row][(dc > 0) ? 3 - i : i] != newVal) {
                        board[row][(dc > 0) ? 3 - i : i] = newVal;
                        moved = true;
                    }
                }
            }
        }
        return moved;
    }

    private List<Integer> mergeList(List<Integer> list) {
        List<Integer> merged = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (i < list.size() - 1 && list.get(i).equals(list.get(i + 1))) {
                int sum = list.get(i) * 2;
                merged.add(sum);
                score += sum;
                i++;
            } else {
                merged.add(list.get(i));
            }
        }
        return merged;
    }

    private boolean isGameOver() {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (board[i][j] == 0) return false;
                if (i < 3 && board[i][j] == board[i + 1][j]) return false;
                if (j < 3 && board[i][j] == board[i][j + 1]) return false;
            }
        }
        return true;
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("board", board);
        state.put("score", score);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() {
        // ...
    }
}
