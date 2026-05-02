package org.lanclassroom.net.ws;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * STOMP session ↔ clientIp 双向映射。
 *
 * 注意：一台机器同时打开多个浏览器标签 → 同 IP 但多 sessionId。
 * 因此 ip → sessions 是一对多，session → ip 是一对一。
 */
@Component
public class ClientSessionRegistry {

    private final ConcurrentMap<String, String> sessionToIp = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> ipToSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String clientIp) {
        if (sessionId == null || clientIp == null) return;
        sessionToIp.put(sessionId, clientIp);
        ipToSessions
                .computeIfAbsent(clientIp, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public String unregister(String sessionId) {
        if (sessionId == null) return null;
        String ip = sessionToIp.remove(sessionId);
        if (ip != null) {
            Set<String> set = ipToSessions.get(ip);
            if (set != null) {
                set.remove(sessionId);
                if (set.isEmpty()) ipToSessions.remove(ip);
            }
        }
        return ip;
    }

    public String getIpBySession(String sessionId) {
        return sessionId == null ? null : sessionToIp.get(sessionId);
    }

    /** 返回该 IP 名下任一 sessionId（用于单播）。 */
    public String getSessionByIp(String ip) {
        if (ip == null) return null;
        Set<String> set = ipToSessions.get(ip);
        if (set == null || set.isEmpty()) return null;
        return set.iterator().next();
    }

    public Set<String> getSessionsByIp(String ip) {
        if (ip == null) return Collections.emptySet();
        Set<String> set = ipToSessions.get(ip);
        return set == null ? Collections.emptySet() : Set.copyOf(set);
    }

    public boolean isIpConnected(String ip) {
        Set<String> set = ipToSessions.get(ip);
        return set != null && !set.isEmpty();
    }

    public Set<String> connectedIps() {
        return Set.copyOf(ipToSessions.keySet());
    }

    public int sessionCount() {
        return sessionToIp.size();
    }
}
