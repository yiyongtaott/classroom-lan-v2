package org.lanclassroom.net.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DiscoveryMessage 序列化测试 - HELLO 业务文档 §4.1 + 选主扩展 (Bug 8/10)。
 */
class DiscoveryMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hello_serializesAllRequiredFields() throws Exception {
        DiscoveryMessage msg = DiscoveryMessage.hello("node-abc", "v2", true, "DESKTOP-A");

        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"HELLO\""), json);
        assertTrue(json.contains("\"id\":\"node-abc\""), json);
        assertTrue(json.contains("\"version\":\"v2\""), json);
        assertTrue(json.contains("\"host\":true"), json);
        assertTrue(json.contains("\"hostname\":\"DESKTOP-A\""), json);
    }

    @Test
    void hello_roundTrip_preservesFields() throws Exception {
        DiscoveryMessage original = DiscoveryMessage.hello("node-xyz", "v2", false, "DESKTOP-B");
        String json = mapper.writeValueAsString(original);

        DiscoveryMessage parsed = mapper.readValue(json, DiscoveryMessage.class);

        assertEquals("HELLO", parsed.getType());
        assertEquals("node-xyz", parsed.getId());
        assertEquals("v2", parsed.getVersion());
        assertEquals(false, parsed.isHost());
        assertEquals("DESKTOP-B", parsed.getHostname());
        assertTrue(parsed.isHello());
    }

    @Test
    void parse_minimalDocumentExample() throws Exception {
        // 业务文档原版三字段 - 兼容性
        String json = "{\"type\":\"HELLO\",\"id\":\"nodeId\",\"version\":\"v2\"}";

        DiscoveryMessage parsed = mapper.readValue(json, DiscoveryMessage.class);

        assertTrue(parsed.isHello());
        assertEquals("nodeId", parsed.getId());
        assertEquals("v2", parsed.getVersion());
        assertEquals(false, parsed.isHost());
    }

    @Test
    void parse_unknownType_isNotHello() throws Exception {
        DiscoveryMessage parsed = mapper.readValue(
                "{\"type\":\"OTHER\",\"id\":\"x\",\"version\":\"v2\"}",
                DiscoveryMessage.class);
        assertEquals(false, parsed.isHello());
    }
}
