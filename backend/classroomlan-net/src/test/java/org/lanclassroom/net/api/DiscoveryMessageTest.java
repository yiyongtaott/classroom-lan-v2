package org.lanclassroom.net.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DiscoveryMessage 序列化测试 - 验证 HELLO 报文严格对齐业务文档 §4.1。
 *
 *   { "type":"HELLO", "id":"nodeId", "version":"v2" }
 */
class DiscoveryMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hello_serializesAllRequiredFields() throws Exception {
        DiscoveryMessage msg = DiscoveryMessage.hello("node-abc", "v2");

        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"HELLO\""), json);
        assertTrue(json.contains("\"id\":\"node-abc\""), json);
        assertTrue(json.contains("\"version\":\"v2\""), json);
    }

    @Test
    void hello_roundTrip_preservesFields() throws Exception {
        DiscoveryMessage original = DiscoveryMessage.hello("node-xyz", "v2");
        String json = mapper.writeValueAsString(original);

        DiscoveryMessage parsed = mapper.readValue(json, DiscoveryMessage.class);

        assertEquals("HELLO", parsed.getType());
        assertEquals("node-xyz", parsed.getId());
        assertEquals("v2", parsed.getVersion());
        assertTrue(parsed.isHello());
    }

    @Test
    void parse_minimalDocumentExample() throws Exception {
        // Exact format from business doc §4.1
        String json = "{\"type\":\"HELLO\",\"id\":\"nodeId\",\"version\":\"v2\"}";

        DiscoveryMessage parsed = mapper.readValue(json, DiscoveryMessage.class);

        assertTrue(parsed.isHello());
        assertEquals("nodeId", parsed.getId());
        assertEquals("v2", parsed.getVersion());
    }

    @Test
    void parse_unknownType_isNotHello() throws Exception {
        DiscoveryMessage parsed = mapper.readValue(
                "{\"type\":\"OTHER\",\"id\":\"x\",\"version\":\"v2\"}",
                DiscoveryMessage.class);
        assertEquals(false, parsed.isHello());
    }
}
