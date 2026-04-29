package org.lanclassroom.core.model;

import java.util.UUID;

/**
 * 玩家实体
 */
public class Player {
    private String id;          // 玩家唯一标识（UUID）
    private String name;        // 玩家昵称
    private boolean host;       // 是否为房主
    private long joinTime;      // 加入时间戳
    private String roomKey;     // 所在房间密钥

    public Player(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.host = false;
        this.joinTime = System.currentTimeMillis();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isHost() { return host; }
    public void setHost(boolean host) { this.host = host; }

    public long getJoinTime() { return joinTime; }
    public void setJoinTime(long joinTime) { this.joinTime = joinTime; }

    public String getRoomKey() { return roomKey; }
    public void setRoomKey(String roomKey) { this.roomKey = roomKey; }
}
