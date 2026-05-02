package org.lanclassroom.net.discovery;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Host 选举器 - 纯逻辑，无 IO，可独立单测。
 *
 * 选举优先级（避免重启时的脑裂争抢）：
 *   1. 自认 Host（host=true）的节点优先  ← Bug 8 修复关键
 *   2. version DESC
 *   3. id ASC
 *
 * 这样：
 *   - 旧 Host a 关闭，b 升级为 Host
 *   - a 重启发 host=true，b 也仍 host=true → 脑裂候选
 *   - 此时 step 2/3 决出 → 双方收敛到同一个 host
 *   - 输的一方下一轮发 host=false → 又收敛
 *
 * 单调性保证：只要节点都按选举结果更新自己的 host 状态再广播，集群必收敛。
 */
public class HostElector {

    private final String selfId;
    private final String selfVersion;
    private final long peerTtlMs;
    private final LongSupplier clock;

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    /**
     * "上一次电选我是否当选 Host"。被 sendHello 读取广播；
     * 启动时为 false（保守），首次 electHost 后会刷新为真实结果。
     */
    private volatile boolean selfHostBelief = false;

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
    public void onPeer(String peerId, String peerVersion, boolean peerIsHost, String peerHostname) {
        if (peerId == null || peerId.equals(selfId)) {
            return;
        }
        peers.put(peerId, new PeerInfo(peerId, peerVersion == null ? "" : peerVersion,
                peerIsHost, peerHostname, clock.getAsLong()));
    }

    /** 旧签名兼容入口 - 测试用。 */
    public void onPeer(String peerId, String peerVersion) {
        onPeer(peerId, peerVersion, false, null);
    }

    /** 选举：返回当前应当作 Host 的节点 id。同时刷新 selfHostBelief。 */
    public String electHost() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);

        // (host=true 优先) → (version DESC) → (id ASC)
        Comparator<PeerInfo> cmp = Comparator
                .comparing((PeerInfo p) -> p.isHost ? 0 : 1)              // host=true 排前
                .thenComparing(p -> p.version, Comparator.reverseOrder()) // version DESC
                .thenComparing(p -> p.id);                                // id ASC

        PeerInfo winner = new PeerInfo(selfId, selfVersion, selfHostBelief, null, now);
        for (Map.Entry<String, PeerInfo> e : peers.entrySet()) {
            PeerInfo candidate = e.getValue();
            if (cmp.compare(candidate, winner) < 0) {
                winner = candidate;
            }
        }
        // 反馈到 selfHostBelief，下次广播 HELLO 携带最新结果
        boolean newBelief = selfId.equals(winner.id);
        selfHostBelief = newBelief;
        return winner.id;
    }

    /** 当前节点是否是 Host。 */
    public boolean isHost() {
        return selfId.equals(electHost());
    }

    public String getSelfId() { return selfId; }
    public String getSelfVersion() { return selfVersion; }

    public int peerCount() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);
        return peers.size();
    }

    /** 测试 / 监控用：返回 peer 快照（不可变副本）。 */
    public Map<String, PeerInfo> snapshotPeers() {
        return Map.copyOf(peers);
    }

    /** 通过 id 查 peer 的 hostname；找不到返回 null。 */
    public String hostnameOf(String peerId) {
        if (selfId.equals(peerId)) return null;
        PeerInfo p = peers.get(peerId);
        return p == null ? null : p.hostname;
    }

    public static final class PeerInfo {
        public final String id;
        public final String version;
        public final boolean isHost;
        public final String hostname;
        public final long lastSeenMs;

        PeerInfo(String id, String version, boolean isHost, String hostname, long lastSeenMs) {
            this.id = id;
            this.version = version;
            this.isHost = isHost;
            this.hostname = hostname;
            this.lastSeenMs = lastSeenMs;
        }

        public String getId() { return id; }
        public String getVersion() { return version; }
        public boolean isHost() { return isHost; }
        public String getHostname() { return hostname; }
        public long getLastSeenMs() { return lastSeenMs; }
    }
}
