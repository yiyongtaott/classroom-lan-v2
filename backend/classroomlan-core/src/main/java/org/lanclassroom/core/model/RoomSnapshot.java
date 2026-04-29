package org.lanclassroom.core.model;

import java.util.List;

/**
 * 房间快照 DTO - 用于客户端同步完整房间状态
 */
public class RoomSnapshot {
    private String roomKey;
    private GameType gameType;
    private List<Player> players;
    private boolean active;

    // Getters & Setters
    public String getRoomKey() { return roomKey; }
    public void setRoomKey(String roomKey) { this.roomKey = roomKey; }

    public GameType getGameType() { return gameType; }
    public void setGameType(GameType gameType) { this.gameType = gameType; }

    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
