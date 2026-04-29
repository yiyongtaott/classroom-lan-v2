package org.lanclassroom.net.discovery;

import org.lanclassroom.net.api.DiscoveryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HostElector 选举逻辑测试
 */
public class HostElectorTest {

    private HostElector elector;

    @BeforeEach
    void setUp() {
        elector = new HostElector();
    }

    @Test
    void initialState_notHost() {
        assertFalse(elector.isHost());
    }

    @Test
    void onHello_whenNoHigherPriorityNode_becomesHost() {
        // Given
        DiscoveryMessage hello = new DiscoveryMessage("HELLO", false, "msg-id", elector.getNodeId());

        // When
        elector.onMessage(hello);

        // Then
        assertTrue(elector.isHost());
        assertNotNull(elector.getRoomKey());
    }

    @Test
    void onHello_whenHigherPriorityNode_arrives_losesHost() {
        // Given: 先成为 Host
        elector.onMessage(new DiscoveryMessage("HELLO", false, "id1", elector.getNodeId()));
        assertTrue(elector.isHost());

        // When: 收到一个 nodeId 更小的节点的 HELLO
        String smallerNodeId = "AAAA"; // 字母序更小
        elector.onMessage(new DiscoveryMessage("HELLO", false, "id2", smallerNodeId));

        // Then
        assertFalse(elector.isHost());
    }

    @Test
    void onBeat_fromOtherHost_updatesTimestamp() {
        // Given
        elector.onMessage(new DiscoveryMessage("BEAT", true, "beat-id", "node-other"));

        // When & Then
        assertTrue(elector.getLastBeatTimestamp() > 0);
    }
}
