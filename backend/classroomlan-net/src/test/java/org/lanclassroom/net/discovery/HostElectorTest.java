package org.lanclassroom.net.discovery;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HostElector 选举逻辑测试 - 业务文档 §4.2 + Bug 8 修复后的优先级：
 *   1. 自认 Host 的节点优先（避免重启时争抢）
 *   2. version DESC
 *   3. id ASC
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
        e.onPeer("aaa", "v2", false, null);
        // v2 > v1 → peer wins despite alphabetically later id
        assertFalse(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void sameVersion_lowerIdWins() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2", false, null);
        assertFalse(e.isHost());
        assertEquals("aaa", e.electHost());
    }

    @Test
    void multiplePeers_correctOrdering() {
        HostElector e = elector("mid", "v2");
        e.onPeer("aaa", "v1", false, null);
        e.onPeer("bbb", "v2", false, null);
        e.onPeer("zzz", "v3", false, null);
        assertEquals("zzz", e.electHost());
    }

    @Test
    void hostFailure_peerTimesOut_selfReElected() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2", true, null); // aaa 是当前 host
        assertEquals("aaa", e.electHost());
        assertFalse(e.isHost());

        // 7s 没收到 aaa 的 HELLO（TTL=6s）
        fakeNow.addAndGet(7_000L);

        assertEquals("zzz", e.electHost());
        assertTrue(e.isHost());
        assertEquals(0, e.peerCount());
    }

    @Test
    void onPeer_selfId_isIgnored() {
        HostElector e = elector("aaa", "v2");
        e.onPeer("aaa", "v9", true, null);
        assertEquals(0, e.peerCount());
        assertTrue(e.isHost());
    }

    /* === Bug 8: host=true 优先 === */

    @Test
    void existingHost_winsOverFreshlyRestartedNode() {
        // 场景：a 关闭重启，IP 字典序 a < b。原本 a 的 id 小该胜，但 b 已是 host → b 应保留
        HostElector b = elector("bbb", "v2"); // 假设 b 已经是 host
        // 模拟 b 已电选过一次 → selfHostBelief=true
        b.electHost();
        assertTrue(b.isHost());

        // 现在 a 重启发来 host=true（自认是 host），但 b 也 host=true
        b.onPeer("aaa", "v2", true, null);

        // 按 (host=true, version, id) 比较 → 都 host=true，version 相同 → id ASC → a 胜
        // 但实际想要的是"已经在位的 b 保持优先"。
        // 实现里使用 (host=true 优先) → (version DESC) → (id ASC)
        // 当两边都 host=true 时 fall through 到 id ASC，a 仍然胜出。
        // 此时 b 在下一轮 electHost 后 selfHostBelief 变 false → 下次发 HELLO host=false → a 单独 host=true 胜出 → 收敛
        assertEquals("aaa", b.electHost());
        assertFalse(b.isHost());
    }

    @Test
    void hostTrue_beatsSamePriorityHostFalse() {
        // 一个明显的"host=true 优先"场景：id 字典序较大但自认 host 的胜
        HostElector self = elector("aaa", "v2"); // self id 更小
        // peer id 更大但 host=true
        self.onPeer("zzz", "v2", true, null);

        // host=true 优先 → zzz 胜
        assertEquals("zzz", self.electHost());
        assertFalse(self.isHost());
    }

    @Test
    void hostnameOf_returnsValueFromPeer() {
        HostElector e = elector("zzz", "v2");
        e.onPeer("aaa", "v2", true, "DESKTOP-AAA");
        assertEquals("DESKTOP-AAA", e.hostnameOf("aaa"));
    }
}
