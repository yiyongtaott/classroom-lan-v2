package org.lanclassroom.net.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscoveryMessage {
    public enum Type {
        HELLO,
        HOST_QUERY,      // 询问“谁是 Host？”
        HOST_REPLY,      // 回复“当前 Host 是 …”
        HOST_CLAIM       // 宣布“我是 Host”或“降级，Host 是 …”
    }

    private Type type;
    private String id;
    private String version;
    private boolean host;       // 仅 HELLO 有用
    private String hostname;
    private String hostId;      // 用于 HOST_REPLY / HOST_CLAIM
    private Instant timestamp;

    public DiscoveryMessage() {}

    // 原有工厂方法
    public static DiscoveryMessage hello(String id, String version, boolean host, String hostname) {
        DiscoveryMessage m = new DiscoveryMessage();
        m.type = Type.HELLO;
        m.id = id;
        m.version = version;
        m.host = host;
        m.hostname = hostname;
        m.timestamp = Instant.now();
        return m;
    }

    // 新增工厂方法
    public static DiscoveryMessage hostQuery(String id) {
        DiscoveryMessage m = new DiscoveryMessage();
        m.type = Type.HOST_QUERY;
        m.id = id;
        m.timestamp = Instant.now();
        return m;
    }

    public static DiscoveryMessage hostReply(String id, String hostId) {
        DiscoveryMessage m = new DiscoveryMessage();
        m.type = Type.HOST_REPLY;
        m.id = id;
        m.hostId = hostId;
        m.timestamp = Instant.now();
        return m;
    }

    public static DiscoveryMessage hostClaim(String id, String hostId) {
        DiscoveryMessage m = new DiscoveryMessage();
        m.type = Type.HOST_CLAIM;
        m.id = id;
        m.hostId = hostId;
        m.timestamp = Instant.now();
        return m;
    }

    public boolean isHello() { return type == Type.HELLO; }
}