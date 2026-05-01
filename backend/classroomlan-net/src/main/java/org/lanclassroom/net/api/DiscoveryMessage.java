package org.lanclassroom.net.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * UDP 发现报文 - HELLO（业务文档 §4.1）。
 * <pre>
 * { "type":"HELLO", "id":"nodeId", "version":"v2" }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryMessage {

    public static final String TYPE_HELLO = "HELLO";

    @JsonProperty("type")
    private String type;

    @JsonProperty("id")
    private String id;

    @JsonProperty("version")
    private String version;

    @JsonProperty("timestamp")
    private long timestamp;

    public DiscoveryMessage() {}

    public DiscoveryMessage(String type, String id, String version) {
        this.type = type;
        this.id = id;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
    }

    public static DiscoveryMessage hello(String id, String version) {
        return new DiscoveryMessage(TYPE_HELLO, id, version);
    }

    @JsonIgnore
    public boolean isHello() {
        return TYPE_HELLO.equals(type);
    }

    public String getType() { return type; }
    public DiscoveryMessage setType(String type) { this.type = type; return this; }

    public String getId() { return id; }
    public DiscoveryMessage setId(String id) { this.id = id; return this; }

    public String getVersion() { return version; }
    public DiscoveryMessage setVersion(String version) { this.version = version; return this; }

    public long getTimestamp() { return timestamp; }
    public DiscoveryMessage setTimestamp(long timestamp) { this.timestamp = timestamp; return this; }

    @Override
    public String toString() {
        return "DiscoveryMessage{type='" + type + "', id='" + id + "', version='" + version + "'}";
    }
}
