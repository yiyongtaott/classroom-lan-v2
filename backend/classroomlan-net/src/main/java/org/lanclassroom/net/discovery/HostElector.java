package org.lanclassroom.net.discovery;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HostElector {

    private final String selfId;
    private final String selfVersion;
    private final long peerTtlMs;
    private final LongSupplier clock;

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile boolean selfHostBelief = false;

    /** 当前集群公认的 Host（即上次选举的获胜者），用于稳定性保证 */
    private volatile String currentHostId = null;

    private final List<HostChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface HostChangeListener {
        void onHostChange(boolean isHost, String hostId);
    }

    public HostElector(String selfId, String selfVersion, long peerTtlMs) {
        this(selfId, selfVersion, peerTtlMs, System::currentTimeMillis);
    }

    public HostElector(String selfId, String selfVersion, long peerTtlMs, LongSupplier clock) {
        this.selfId = Objects.requireNonNull(selfId);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.peerTtlMs = peerTtlMs;
        this.clock = clock;
    }

    public void onPeer(String peerId, String peerVersion, boolean peerIsHost, String peerHostname) {
        if (peerId == null || peerId.equals(selfId)) return;
        peers.put(peerId, new PeerInfo(peerId, peerVersion == null ? "" : peerVersion,
                peerIsHost, peerHostname, clock.getAsLong()));
    }

    public void onPeer(String peerId, String peerVersion) {
        onPeer(peerId, peerVersion, false, null);
    }

    /**
     * 主动退位：当前 Host 节点退出时调用，清空 currentHostId 并重新选举。
     * 一般由 DiscoveryService.destroy() 触发。
     */
    public void resign() {
        if (selfId.equals(currentHostId)) {
            currentHostId = null;
            // 触发一次选举，会选出新的 Host
            electHost();
        }
    }

    public String electHost() {
        long now = clock.getAsLong();
        // 1. 清理超时节点
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);

        // 2. 如果已有 currentHostId 且该节点仍然存活，则保持不变
        if (currentHostId != null && isAlive(currentHostId, now)) {
            boolean newBelief = selfId.equals(currentHostId);
            if (newBelief != selfHostBelief) {
                selfHostBelief = newBelief;
                notifyListeners(newBelief, currentHostId);
            }
            return currentHostId;
        }

        // 3. 否则重新选举：存活节点中 ID 最小的
        Comparator<PeerInfo> cmp = Comparator.comparing(p -> p.id);
        PeerInfo winner = new PeerInfo(selfId, selfVersion, selfHostBelief, null, now);
        for (Map.Entry<String, PeerInfo> e : peers.entrySet()) {
            PeerInfo candidate = e.getValue();
            if (cmp.compare(candidate, winner) < 0) {
                winner = candidate;
            }
        }
        String newHostId = winner.id;

        // 4. 更新 currentHostId 并通知
        if (!newHostId.equals(currentHostId)) {
            currentHostId = newHostId;
            boolean newBelief = selfId.equals(newHostId);
            selfHostBelief = newBelief;
            notifyListeners(newBelief, newHostId);
        } else {
            // 即使 ID 没变，也要更新 selfHostBelief（可能之前是不一致）
            boolean newBelief = selfId.equals(newHostId);
            if (newBelief != selfHostBelief) {
                selfHostBelief = newBelief;
                notifyListeners(newBelief, newHostId);
            }
        }
        return newHostId;
    }

    public boolean isHost() {
        return selfId.equals(electHost());
    }

    /** 启动时强制重新选举，确保本节点状态正确 */
    public void forceReelectAndNotify() {
        electHost();
    }

    // 判断指定节点是否存活
    private boolean isAlive(String nodeId, long now) {
        if (selfId.equals(nodeId)) {
            // 自己永远认为自己是存活的（只要进程在）
            return true;
        }
        PeerInfo info = peers.get(nodeId);
        return info != null && (now - info.lastSeenMs <= peerTtlMs);
    }

    public String getSelfId() { return selfId; }
    public String getSelfVersion() { return selfVersion; }

    public int peerCount() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);
        return peers.size();
    }

    public Map<String, PeerInfo> snapshotPeers() {
        return Map.copyOf(peers);
    }

    public String hostnameOf(String peerId) {
        if (selfId.equals(peerId)) return null;
        PeerInfo p = peers.get(peerId);
        return p == null ? null : p.hostname;
    }

    public void addListener(HostChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(HostChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(boolean isHost, String hostId) {
        for (HostChangeListener listener : listeners) {
            try {
                listener.onHostChange(isHost, hostId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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