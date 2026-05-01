package org.lanclassroom.core.model;

import java.util.UUID;

/**
 * 玩家实体（极简版 - 无 roomKey/token 字段）。
 * avatar 为 host 服务路径，如 "/api/avatars/&lt;playerId&gt;"，没上传时为 null。
 */
public class Player {
    private String id;
    private String name;
    private String hostname;     // 系统名（默认显示）
    private String ip;           // 客户端 IP（host 在加入时回填）
    private String avatar;       // 头像访问 URL（host 提供）
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

    public String getHostname() { return hostname; }
    public Player setHostname(String hostname) { this.hostname = hostname; return this; }

    public String getIp() { return ip; }
    public Player setIp(String ip) { this.ip = ip; return this; }

    public String getAvatar() { return avatar; }
    public Player setAvatar(String avatar) { this.avatar = avatar; return this; }

    public boolean isHost() { return host; }
    public Player setHost(boolean host) { this.host = host; return this; }

    public long getJoinTime() { return joinTime; }
    public Player setJoinTime(long joinTime) { this.joinTime = joinTime; return this; }
}
