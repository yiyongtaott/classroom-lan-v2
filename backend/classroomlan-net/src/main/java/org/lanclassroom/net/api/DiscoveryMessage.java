package org.lanclassroom.net.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * UDP 发现消息 DTO
 */
public class DiscoveryMessage {
    @JsonProperty("type")
    private String type;        // HELLO / BEAT

    @JsonProperty("host")
    private boolean host;       // 是否为 Host

    @JsonProperty("id")
    private String id;          // 节点唯一 ID

    @JsonProperty("nodeId")
    private String nodeId;      // 节点标识

    @JsonProperty("roomKey")
    private String roomKey;     // 房间密钥（Host 发送 BEAT 时携带）

    @JsonProperty("timestamp")
    private long timestamp;     // 时间戳

    public DiscoveryMessage() {}

    public DiscoveryMessage(String type, boolean host, String id, String nodeId) {
        this.type = type;
        this.host = host;
        this.id = id;
        this.nodeId = nodeId;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters & Setters
    public String getType() { return type; }
    public DiscoveryMessage setType(String type) { this.type = type; return this; }

    public boolean isHost() { return host; }
    public DiscoveryMessage setHost(boolean host) { this.host = host; return this; }

    public String getId() { return id; }
    public DiscoveryMessage setId(String id) { this.id = id; return this; }

    public String getNodeId() { return nodeId; }
    public DiscoveryMessage setNodeId(String nodeId) { this.nodeId = nodeId; return this; }

    public String getRoomKey() { return roomKey; }
    public DiscoveryMessage setRoomKey(String roomKey) { this.roomKey = roomKey; return this; }

    public long getTimestamp() { return timestamp; }
    public DiscoveryMessage setTimestamp(long timestamp) { this.timestamp = timestamp; return this; }
}
