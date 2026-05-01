package org.lanclassroom.core.model;

import java.util.List;

/**
 * 房间快照 - 客户端连接 / 重连时拉取的完整视图。
 */
public class RoomSnapshot {
    private String hostNodeId;
    private GameType gameType;
    private List<Player> players;
    private int playerCount;

    public String getHostNodeId() { return hostNodeId; }
    public void setHostNodeId(String hostNodeId) { this.hostNodeId = hostNodeId; }

    public GameType getGameType() { return gameType; }
    public void setGameType(GameType gameType) { this.gameType = gameType; }

    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
}
