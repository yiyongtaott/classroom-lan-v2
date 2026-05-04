package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FiveInARowGame implements GameSession {

    private Room room;
    private Broadcaster broadcaster;
    private int[][] board = new int[15][15];
    private List<String> players = new ArrayList<>();
    private int turnIdx = 0;
    private boolean gameOver = false;

    @Override
    public GameType getType() {
        return GameType.FIVE_IN_A_ROW;
    }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.board = new int[15][15];
        this.gameOver = false;
        this.turnIdx = 0;

        // 选取前两名在线玩家
        this.players = room.getPlayers().stream()
                .filter(p -> Player.STATUS_ONLINE.equals(p.getStatus()))
                .map(Player::getId)
                .limit(2)
                .toList();

        Map<String, Object> start = new HashMap<>();
        start.put("type", getType().name());
        start.put("stage", "START");
        start.put("players", players);
        start.put("turn", players.isEmpty() ? null : players.get(0));
        start.put("board", board);
        broadcaster.broadcast(start);
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver || players.size() < 2) return;

        // 校验是否是当前玩家的回合
        String currentTurnPlayerId = players.get(turnIdx);
        if (!Objects.equals(player.getId(), currentTurnPlayerId)) return;

        int x = (int) payload.getOrDefault("x", -1);
        int y = (int) payload.getOrDefault("y", -1);

        if (x < 0 || x >= 15 || y < 0 || y >= 15 || board[x][y] != 0) return;

        int piece = (turnIdx == 0) ? 1 : 2;
        board[x][y] = piece;

        boolean won = checkWin(x, y);
        boolean draw = checkDraw();
        String winnerId = won ? currentTurnPlayerId : null;

        // 切换回合
        turnIdx = (turnIdx + 1) % players.size();

        Map<String, Object> response = new HashMap<>();
        response.put("type", getType().name());
        response.put("stage", "MOVE");
        response.put("x", x);
        response.put("y", y);
        response.put("player", piece);
        response.put("turn", players.get(turnIdx));
        response.put("won", won);
        response.put("winner", winnerId);
        response.put("draw", draw);

        if (won || draw) {
            gameOver = true;
        }

        broadcaster.broadcast(response);
    }

    private boolean checkDraw() {
        for (int[] row : board) {
            for (int cell : row) {
                if (cell == 0) return false;
            }
        }
        return true;
    }

    private boolean checkWin(int x, int y) {
        int player = board[x][y];
        return checkDirection(x, y, 1, 0, player) ||
               checkDirection(x, y, 0, 1, player) ||
               checkDirection(x, y, 1, 1, player) ||
               checkDirection(x, y, 1, -1, player);
    }

    private boolean checkDirection(int x, int y, int dx, int dy, int player) {
        int count = 1;
        count += countInDirection(x, y, dx, dy, player);
        count += countInDirection(x, y, -dx, -dy, player);
        return count >= 5;
    }

    private int countInDirection(int x, int y, int dx, int dy, int player) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;
        while (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && board[nx][ny] == player) {
            count++;
            nx += dx;
            ny += dy;
        }
        return count;
    }

    @Override
    public void stop() {
        Map<String, Object> stop = new HashMap<>();
        stop.put("type", getType().name());
        stop.put("stage", "STOP");
        broadcaster.broadcast(stop);
    }
}
