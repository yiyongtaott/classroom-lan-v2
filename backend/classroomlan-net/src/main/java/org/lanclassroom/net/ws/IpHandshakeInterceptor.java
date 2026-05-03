package org.lanclassroom.net.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * 握手阶段一次性绑定 clientIp。
 * 后续业务从 SimpMessageHeaderAccessor 取出 attribute 即可，不再每次读 RemoteAddr。
 */
@Component
public class IpHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IpHandshakeInterceptor.class);

    public static final String ATTR_CLIENT_IP = "clientIp";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ip = resolveIp(request);
        attributes.put(ATTR_CLIENT_IP, ip);
//        log.debug("[Handshake] clientIp={}", ip);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String resolveIp(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest s) {
            String forwarded = s.getServletRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String ip = s.getServletRequest().getRemoteAddr();
            return transferIp(ip);
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) return null;
        InetAddress addr = remote.getAddress();
        String ip = addr == null ? null : addr.getHostAddress();
        return transferIp(ip);
    }

    private static String transferIp(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip)) ip = "127.0.0.1";
        if ("127.0.0.1".equals(ip)) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                log.error("无法获取本地局域网IP", e);
            }
        }
        return ip;
    }
}
