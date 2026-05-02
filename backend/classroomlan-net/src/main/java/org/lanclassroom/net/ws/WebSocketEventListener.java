package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.service.ChatHistoryService;
import org.lanclassroom.net.service.GameHistoryService;
import org.lanclassroom.net.service.GameInvitationService;
import org.lanclassroom.net.service.StatusBroadcastService;
import org.lanclassroom.net.service.UserStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 生命周期处理器：
 *   连接建立 → 注册 ClientSessionRegistry，向 /user/{sessionId}/queue/init 推快照
 *   连接断开 → 注销，触发 UserStatusService 状态联动
 *
 * 所有原本分散在 ConnectionTracker 的"基于 STOMP 事件"的代码集中到此处；
 * ConnectionTracker 仍然保留，处理"基于 player.online 帧"的 sessionCount。
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final ClientSessionRegistry registry;
    private final SimpMessagingTemplate messaging;
    private final StatusBroadcastService statusService;
    private final ChatHistoryService chatHistory;
    private final GameHistoryService gameHistory;
    private final GameInvitationService invitations;
    private final UserStatusService userStatus;
    private final Room room;

    public WebSocketEventListener(ClientSessionRegistry registry,
                                  SimpMessagingTemplate messaging,
                                  StatusBroadcastService statusService,
                                  ChatHistoryService chatHistory,
                                  GameHistoryService gameHistory,
                                  GameInvitationService invitations,
                                  UserStatusService userStatus,
                                  Room room) {
        this.registry = registry;
        this.messaging = messaging;
        this.statusService = statusService;
        this.chatHistory = chatHistory;
        this.gameHistory = gameHistory;
        this.invitations = invitations;
        this.userStatus = userStatus;
        this.room = room;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent ev) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(ev.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        String clientIp = sessionAttrs == null
                ? null
                : (String) sessionAttrs.get(IpHandshakeInterceptor.ATTR_CLIENT_IP);

        if (sessionId != null && clientIp != null) {
            registry.register(sessionId, clientIp);
            log.info("[WS] connected sid={} ip={}", sessionId, clientIp);

            // ws 状态推送 - 只要任意 session 在线，该 ip 关联的 player 即 wsAlive=true
            Optional<Player> p = room.findByIp(clientIp);
            p.ifPresent(player -> userStatus.setWsAlive(player.getId(), true));

            sendInitSnapshot(sessionId, clientIp);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent ev) {
        String sessionId = ev.getSessionId();
        String ip = registry.unregister(sessionId);
        log.info("[WS] disconnected sid={} ip={}", sessionId, ip);

        if (ip != null && !registry.isIpConnected(ip)) {
            // 该 IP 全部 sessions 都断了 → wsAlive = false
            room.findByIp(ip).ifPresent(player ->
                    userStatus.setWsAlive(player.getId(), false));
        }
    }

    /** 向单个 session 推送 init 快照：合并原 /api/status + /api/room + 历史 + 当前邀请。 */
    private void sendInitSnapshot(String sessionId, String clientIp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", statusService.buildStatusFor(clientIp));
        payload.put("room", statusService.buildRoomSnapshot());
        payload.put("chatHistory", chatHistory.all());
        payload.put("gameHistory", gameHistory.all());
        payload.put("invitation", invitations.getCurrent());
        payload.put("userStatuses", userStatus.snapshot());

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(sessionId, WebSocketConfig.QUEUE_INIT, payload,
                headers.getMessageHeaders());
    }
}
