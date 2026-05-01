package org.lanclassroom.core.model;

import java.util.UUID;

/**
 * 玩家实体（极简版 - 无 roomKey/token 字段）。
 */
public class Player {
    private String id;
    private String name;
    private boolean host;
    private long joinTime;

    public Player() {}

    public Player(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.host = false;
        this.joinTime = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public Player setId(String id) { this.id = id; return this; }

    public String getName() { return name; }
    public Player setName(String name) { this.name = name; return this; }

    public boolean isHost() { return host; }
    public Player setHost(boolean host) { this.host = host; return this; }

    public long getJoinTime() { return joinTime; }
    public Player setJoinTime(long joinTime) { this.joinTime = joinTime; return this; }
}
