package org.lanclassroom.net.discovery;

import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
@Data
public class HostElector {

    private final String selfId;
    private final String selfVersion;
    private final long peerTtlMs;
    private final LongSupplier clock;

    // 节点记录（仅用于心跳超时检测和 hostname 查询）
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    // 当前公认的 Host
    private volatile String currentHostId = null;
    private volatile boolean selfHostBelief = false;

    private final List<HostChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface HostChangeListener {
        void onHostChange(boolean isHost, String hostId);
    }

    // 三参数构造，兼容 AppConfig 的 Bean 定义
    public HostElector(String selfId, String selfVersion, long peerTtlMs) {
        this(selfId, selfVersion, peerTtlMs, System::currentTimeMillis);
    }

    public HostElector(String selfId, String selfVersion, long peerTtlMs, LongSupplier clock) {
        this.selfId = Objects.requireNonNull(selfId);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.peerTtlMs = peerTtlMs;
        this.clock = clock;
    }

    /** 接收其他节点的 HELLO 信息，用于记录 peer（保留兼容） */
    public void onPeer(String peerId, String peerVersion, boolean peerIsHost, String peerHostname) {
        if (peerId == null || peerId.equals(selfId)) return;
        peers.put(peerId, new PeerInfo(peerId, peerVersion == null ? "" : peerVersion,
                peerIsHost, peerHostname, clock.getAsLong()));
    }

    public void onPeer(String peerId, String peerVersion) {
        onPeer(peerId, peerVersion, false, null);
    }

    /** 设置当前 Host（由选举协议调用） */
    public void setHost(String hostId) {
        if (hostId == null) return;
        if (hostId.equals(this.currentHostId)) return;
        this.currentHostId = hostId;
        boolean newBelief = selfId.equals(hostId);
        if (newBelief != selfHostBelief) {
            selfHostBelief = newBelief;
            notifyListeners(newBelief, hostId);
        }
    }

    public String getHostId() {
        return currentHostId;
    }

    public boolean isHost() {
        return selfHostBelief;
    }

    public int peerCount() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);
        return peers.size();
    }

    // 心跳超时清理，保留用于可能的后续状态判断
    public void cleanupExpiredPeers() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);
    }

    public String hostnameOf(String peerId) {
        if (selfId.equals(peerId)) return null;
        PeerInfo p = peers.get(peerId);
        return p == null ? null : p.hostname;
    }

    public void addListener(HostChangeListener l) { listeners.add(l); }
    public void removeListener(HostChangeListener l) { listeners.remove(l); }

    private void notifyListeners(boolean isHost, String hostId) {
        for (HostChangeListener l : listeners) {
            try { l.onHostChange(isHost, hostId); } catch (Exception e) { e.printStackTrace(); }
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
    }
}