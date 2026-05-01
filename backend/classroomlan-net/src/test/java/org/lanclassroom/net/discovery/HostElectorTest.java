package org.lanclassroom.net.discovery;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HostElector 选举逻辑测试 - 业务文档 §4.2 优先级：version DESC → nodeId ASC。
 */
class HostElectorTest {

    private final AtomicLong fakeNow = new AtomicLong(1_000_000L);

    private HostElector elector(String selfId, String version) {
        return new HostElector(selfId, version, 6_000L, fakeNow::get);
    }

    @Test
    void noPeers_selfIsHost() {
        HostElector e = elector("aaa", "v2");
        assertTrue(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void higherVersion_winsRegardlessOfId() {
        HostElector e = elector("zzz", "v1");
        e.onPeer("aaa", "v2");
        // v2 > v1 → peer wins despite alphabetically later id
        assertFalse(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void sameVersion_lowerIdWins() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2");
        assertFalse(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void sameVersion_selfHasLowerId_selfWins() {
        HostElector e = elector("aaa", "v2");
        e.onPeer("zzz", "v2");
        assertTrue(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void multiplePeers_correctOrdering() {
        HostElector e = elector("mid", "v2");
        e.onPeer("aaa", "v1");   // lower version, irrelevant
        e.onPeer("bbb", "v2");   // same version, lexically lower → wins
        e.onPeer("zzz", "v3");   // higher version → wins overall
        assertEquals("zzz", e.electHost());
        assertFalse(e.isHost());
    }

    @Test
    void hostFailure_peerTimesOut_selfReElected() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2");
        assertEquals("aaa", e.electHost());
        assertFalse(e.isHost());

        // simulate 7 seconds without any HELLO from peer "aaa" (TTL = 6s)
        fakeNow.addAndGet(7_000L);

        assertEquals("zzz", e.electHost());
        assertTrue(e.isHost());
        assertEquals(0, e.peerCount());
    }

    @Test
    void onPeer_selfId_isIgnored() {
        HostElector e = elector("aaa", "v2");
        e.onPeer("aaa", "v9");   // self HELLO must NOT poison the registry
        assertEquals(0, e.peerCount());
        assertTrue(e.isHost());
    }

    @Test
    void onPeer_updatesLastSeen_extendingTtl() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2");

        // halfway through TTL, refresh
        fakeNow.addAndGet(4_000L);
        e.onPeer("aaa", "v2");

        // another 4 seconds — still alive (4 < 6)
        fakeNow.addAndGet(4_000L);
        assertEquals(1, e.peerCount());
        assertEquals("aaa", e.electHost());
    }
}
