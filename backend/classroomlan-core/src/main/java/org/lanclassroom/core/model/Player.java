package org.lanclassroom.core.model;

import lombok.Data;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 玩家实体（极简版）。
 * status 取值：
 *   ONLINE       - 浏览器活跃 + 后端 jar 在线
 *   PAGE_CLOSED  - 浏览器关了，但后端 jar 仍在 LAN（保留账号信息）
 *   OFFLINE      - 后端 jar 也下线（即将清除）
 */
@Data
@Component
public class Player {

    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_PAGE_CLOSED = "PAGE_CLOSED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    private String id;
    private String name;
    private String hostname;
    private String ip;
    private String avatar;
    private boolean host;
    private long joinTime;
    /** 在线状态 - {@link #STATUS_ONLINE} / {@link #STATUS_PAGE_CLOSED} / {@link #STATUS_OFFLINE} */
    private String status = STATUS_ONLINE;
    /** 最近一次 WebSocket 活跃时间（ms） */
    private long lastSeenMs;
    /** 当前已连接的 STOMP session 数（>0 表示至少有一个浏览器开着该账号） */
    private int sessionCount;

    public Player() {}

    public Player(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.host = false;
        this.joinTime = System.currentTimeMillis();
        this.lastSeenMs = this.joinTime;
    }

    public Player setId(String id) { this.id = id; return this; }

    public Player setName(String name) { this.name = name; return this; }

    public Player setHostname(String hostname) { this.hostname = hostname; return this; }

    public Player setIp(String ip) { this.ip = ip; return this; }

    public Player setAvatar(String avatar) { this.avatar = avatar; return this; }

    public Player setHost(boolean host) { this.host = host; return this; }

    public Player setJoinTime(long joinTime) { this.joinTime = joinTime; return this; }

    public Player setStatus(String status) { this.status = status; return this; }

    public Player setLastSeenMs(long lastSeenMs) { this.lastSeenMs = lastSeenMs; return this; }

    public Player setSessionCount(int sessionCount) { this.sessionCount = sessionCount; return this; }

    public Player incrementSession() {
        this.sessionCount++;
        this.status = STATUS_ONLINE;
        this.lastSeenMs = System.currentTimeMillis();
        return this;
    }

    public Player decrementSession() {
        if (sessionCount > 0) sessionCount--;
        if (sessionCount == 0) {
            this.status = STATUS_PAGE_CLOSED;
        }
        return this;
    }
}
