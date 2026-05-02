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

    public String electHost() {
        long now = clock.getAsLong();
        peers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > peerTtlMs);

        Comparator<PeerInfo> cmp = Comparator
                .comparing((PeerInfo p) -> p.isHost ? 0 : 1)
                .thenComparing(p -> p.version, Comparator.reverseOrder())
                .thenComparing(p -> p.id);

        PeerInfo winner = new PeerInfo(selfId, selfVersion, selfHostBelief, null, now);
        for (Map.Entry<String, PeerInfo> e : peers.entrySet()) {
            PeerInfo candidate = e.getValue();
            if (cmp.compare(candidate, winner) < 0) {
                winner = candidate;
            }
        }

        boolean newBelief = selfId.equals(winner.id);
        if (newBelief != selfHostBelief) {
            selfHostBelief = newBelief;
            notifyListeners(newBelief, winner.id);
        }
        return winner.id;
    }

    public boolean isHost() {
        return selfId.equals(electHost());
    }

    /** 启动时强制重选举并通知（确保一上线就广播当前 host 状态） */
    public void forceReelectAndNotify() {
        electHost();
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