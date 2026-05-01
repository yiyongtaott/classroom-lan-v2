package org.lanclassroom.net.discovery;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Host 选举器 - 纯逻辑，无 IO，可独立单测。
 *
 * 算法：
 *   1. 维护一个 peer 注册表 {id → (version, lastSeenMs)}
 *   2. 每次询问 isHost(nowMs)：
 *      - 清理 nowMs - lastSeen &gt; peerTtlMs 的节点
 *      - 把自己加入候选集
 *      - 按 (version DESC, id ASC) 排序，第一名即 Host
 *      - 当前节点是否就是 Host？
 *
 * 这是"纯一致性视图"算法：所有节点收到的 HELLO 集合一致 → 算出的 Host 一致。
 * 故障重选自动发生：旧 Host 停止发 HELLO → 6s 后被所有节点剔除 → 重新选举。
 */
public class HostElector {

    private final String selfId;
    private final String selfVersion;
    private final long peerTtlMs;
    private final LongSupplier clock;

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    public HostElector(String selfId, String selfVersion, long peerTtlMs) {
        this(selfId, selfVersion, peerTtlMs, System::currentTimeMillis);
    }

    public HostElector(String selfId, String selfVersion, long peerTtlMs, LongSupplier clock) {
        this.selfId = Objects.requireNonNull(selfId);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.peerTtlMs = peerTtlMs;
        this.clock = clock;
    }

    /** 收到来自其他节点的 HELLO（自己的 HELLO 应被 DiscoveryService 过滤掉）。 */
    public void onPeer(String peerId, String peerVersion) {
        if (peerId == null || peerId.equals(selfId)) {
            return;
        }
        peers.put(peerId, new PeerInfo(peerVersion == null ? "" : peerVersion, clock.getAsLong()));
    }

    /** 返回当前应当作为 Host 的节点 id。 */
    public String electHost() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);

        Comparator<PeerInfo> cmp = Comparator
                .comparing((PeerInfo p) -> p.version, Comparator.reverseOrder())  // version DESC
                .thenComparing(p -> p.id);                                         // id ASC

        PeerInfo winner = new PeerInfo(selfId, selfVersion, now);
        for (Map.Entry<String, PeerInfo> e : peers.entrySet()) {
            PeerInfo candidate = new PeerInfo(e.getKey(), e.getValue().version, e.getValue().lastSeenMs);
            if (cmp.compare(candidate, winner) < 0) {
                winner = candidate;
            }
        }
        return winner.id;
    }

    /** 当前节点是否是 Host。 */
    public boolean isHost() {
        return selfId.equals(electHost());
    }

    public String getSelfId() { return selfId; }
    public String getSelfVersion() { return selfVersion; }

    /** 已知存活 peer 数量（不含自己）。 */
    public int peerCount() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);
        return peers.size();
    }

    /** 测试 / 监控用：返回 peer 快照。 */
    public Map<String, PeerInfo> snapshotPeers() {
        return Map.copyOf(peers);
    }

    public static final class PeerInfo {
        public final String id;
        public final String version;
        public final long lastSeenMs;

        PeerInfo(String version, long lastSeenMs) {
            this.id = null;
            this.version = version;
            this.lastSeenMs = lastSeenMs;
        }

        PeerInfo(String id, String version, long lastSeenMs) {
            this.id = id;
            this.version = version;
            this.lastSeenMs = lastSeenMs;
        }

        public String getVersion() { return version; }
        public long getLastSeenMs() { return lastSeenMs; }
    }
}
