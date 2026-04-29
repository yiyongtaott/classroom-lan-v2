package org.lanclassroom.core.model;

import org.lanclassroom.core.service.GameSession;
import org.lanclassroom.core.model.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 房间实体 - 存储房间状态、玩家列表、游戏会话等
 */
public class Room {
    private String roomKey;              // 房间密钥（动态生成）
    private String hostId;               // Host 节点 ID
    private String hostNodeId;           // Host 节点标识
    private GameType gameType;           // 当前游戏类型
    private GameSession gameSession;     // 游戏会话实例
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private long createdAt;              // 房间创建时间
    private boolean active = true;       // 房间是否活跃

    public Room() {
        this.createdAt = System.currentTimeMillis();
    }

    // 生成新房间密钥（16位十六进制随机串）
    public static String generateRoomKey() {
        return Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    // 房间快照（用于客户端重连时拉取）
    public RoomSnapshot snapshot() {
        RoomSnapshot snap = new RoomSnapshot();
        snap.setRoomKey(roomKey);
        snap.setGameType(gameType);
        snap.setPlayers(new ArrayList<>(players));
        snap.setActive(active);
        return snap;
    }

    // Getters & Setters
    public String getRoomKey() { return roomKey; }
    public Room setRoomKey(String roomKey) { this.roomKey = roomKey; return this; }

    public String getHostId() { return hostId; }
    public Room setHostId(String hostId) { this.hostId = hostId; return this; }

    public String getHostNodeId() { return hostNodeId; }
    public Room setHostNodeId(String hostNodeId) { this.hostNodeId = hostNodeId; return this; }

    public GameType getGameType() { return gameType; }
    public Room setGameType(GameType gameType) { this.gameType = gameType; return this; }

    public GameSession getGameSession() { return gameSession; }
    public Room setGameSession(GameSession gameSession) { this.gameSession = gameSession; return this; }

    public List<Player> getPlayers() { return players; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
