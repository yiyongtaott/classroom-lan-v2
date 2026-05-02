package org.lanclassroom.net.service;

import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.ws.ClientSessionRegistry;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户三状态记录服务（任务 3：UDP 心跳 / WebSocket 连接 / 标签页激活）。
 *
 * 状态一：UDP 心跳 - 来自 DiscoveryService 收到 HELLO 时调用 updateUdpHeartbeat()。
 * 状态二：WebSocket 连接 - 来自 SessionConnect/Disconnect 监听器。
 * 状态三：标签页 active - 来自 /app/user.page-active STOMP 帧。
 */
@Service
public class UserStatusService {

    private static final Logger log = LoggerFactory.getLogger(UserStatusService.class);
    public static final long UDP_TIMEOUT_MS = 6000L;

    private final SimpMessagingTemplate messaging;
    private final DiscoveryService discovery;
    private final ClientSessionRegistry sessions;

    private final Map<String, UserStatusRecord> records = new ConcurrentHashMap<>();

    public UserStatusService(SimpMessagingTemplate messaging,
                             DiscoveryService discovery,
                             ClientSessionRegistry sessions) {
        this.messaging = messaging;
        this.discovery = discovery;
        this.sessions = sessions;
    }

    public UserStatusRecord ensureRecord(String userId) {
        if (userId == null) return null;
        return records.computeIfAbsent(userId, id -> {
            UserStatusRecord r = new UserStatusRecord();
            r.userId = id;
            return r;
        });
    }

    public UserStatusRecord getRecord(String userId) {
        return userId == null ? null : records.get(userId);
    }

    public Set<UserStatusRecord> allRecords() {
        return new HashSet<>(records.values());
    }

    public void updateUdpHeartbeat(String userId, Instant when) {
        if (userId == null) return;
        UserStatusRecord r = ensureRecord(userId);
        Instant t = when == null ? Instant.now() : when;
        r.lastUdpHeartbeat = t;
        boolean newAlive = true;
        if (r.backendAlive != newAlive) {
            r.backendAlive = newAlive;
            broadcastStatusUpdate(r);
        }
    }

    public void setWsAlive(String userId, boolean alive) {
        if (userId == null) return;
        UserStatusRecord r = ensureRecord(userId);
        boolean changed = false;
        if (r.wsAlive != alive) {
            r.wsAlive = alive;
            changed = true;
        }
        // 断开 → 联动置 pageActive=false
        if (!alive && r.pageActive) {
            r.pageActive = false;
            changed = true;
        }
        if (changed) {
            broadcastStatusUpdate(r);
        }
    }

    public boolean getPageActive(String userId) {
        UserStatusRecord r = getRecord(userId);
        return r != null && r.pageActive;
    }

    public void setPageActive(String userId, boolean active) {
        if (userId == null) return;
        UserStatusRecord r = ensureRecord(userId);
        if (r.pageActive != active) {
            r.pageActive = active;
            broadcastStatusUpdate(r);
        }
    }

    /** 给客户端用作 init 快照里的 users.statuses 字段（合并到 player 上）。 */
    public Map<String, UserStatusRecord> snapshot() {
        return Map.copyOf(records);
    }

    @Scheduled(fixedDelay = 3000)
    public void checkUdpAliveness() {
        Instant threshold = Instant.now().minusMillis(UDP_TIMEOUT_MS);
        List<UserStatusRecord> changed = new ArrayList<>();
        for (UserStatusRecord r : records.values()) {
            boolean newVal = r.lastUdpHeartbeat != null && r.lastUdpHeartbeat.isAfter(threshold);
            if (r.backendAlive != newVal) {
                r.backendAlive = newVal;
                changed.add(r);
            }
        }
        for (UserStatusRecord r : changed) {
            broadcastStatusUpdate(r);
        }
    }

    private void broadcastStatusUpdate(UserStatusRecord r) {
        if (r == null) return;
        messaging.convertAndSend(WebSocketConfig.TOPIC_USER_STATUS, toPayload(r));
    }

    public Map<String, Object> toPayload(UserStatusRecord r) {
        return Map.of(
                "userId", r.userId == null ? "" : r.userId,
                "backendAlive", r.backendAlive,
                "wsAlive", r.wsAlive,
                "pageActive", r.pageActive
        );
    }

    /** 持有的状态记录数据结构 - 同时供 Jackson 序列化。 */
    public static class UserStatusRecord {
        public String userId;
        public Instant lastUdpHeartbeat;
        public boolean backendAlive;
        public boolean wsAlive;
        public boolean pageActive;

        public String getUserId() { return userId; }
        public boolean isBackendAlive() { return backendAlive; }
        public boolean isWsAlive() { return wsAlive; }
        public boolean isPageActive() { return pageActive; }
    }
}
