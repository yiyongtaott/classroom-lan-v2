package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * FEATURE-04：私聊与私聊邀请。
 *
 * 服务端不持久化任何私聊消息，只做 STOMP 转发。
 *
 * /app/private.invite              {receiverId} → 给被邀请人发邀请通知
 * /app/private.chat.send           {receiverId, content} → 转发给目标
 *
 * 单播协议：把消息按目标 ip 找到所有 sessionId，逐个 convertAndSendToUser。
 */
@Controller
public class PrivateChatController {

    private static final Logger log = LoggerFactory.getLogger(PrivateChatController.class);

    private final SimpMessagingTemplate messaging;
    private final ClientSessionRegistry sessions;
    private final RoomManager roomManager;

    public PrivateChatController(SimpMessagingTemplate messaging,
                                 ClientSessionRegistry sessions,
                                 Room room) {
        this.messaging = messaging;
        this.sessions = sessions;
        this.roomManager = roomManager;
    }

    @MessageMapping("/private.invite")
    public void invite(@Payload Map<String, Object> payload,
                       SimpMessageHeaderAccessor accessor) {
        String receiverId = strOrNull(payload, "receiverId");
        if (receiverId == null) return;
        String senderId = senderId(accessor);
        if (senderId == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("type", "INVITE");
        body.put("senderId", senderId);
        body.put("receiverId", receiverId);
        body.put("ts", System.currentTimeMillis());
        body.put("senderName", nameOf(senderId));

        forwardToPlayer(receiverId, WebSocketConfig.QUEUE_PRIVATE_INVITE, body);
    }

    @MessageMapping("/private.chat.send")
    public void send(@Payload Map<String, Object> payload,
                     SimpMessageHeaderAccessor accessor) {
        String receiverId = strOrNull(payload, "receiverId");
        String content = strOrNull(payload, "content");
        if (receiverId == null || content == null) return;
        String senderId = senderId(accessor);
        if (senderId == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("senderId", senderId);
        body.put("receiverId", receiverId);
        body.put("content", content);
        body.put("ts", System.currentTimeMillis());

        // 同时回送给发送方，保证发送方界面也能看到自己刚发的消息
        forwardToPlayer(receiverId, WebSocketConfig.QUEUE_PRIVATE_CHAT, body);
        forwardToPlayer(senderId, WebSocketConfig.QUEUE_PRIVATE_CHAT, body);
    }

    @MessageMapping("/private.invite.respond")
    public void inviteRespond(@Payload Map<String, Object> payload,
                              SimpMessageHeaderAccessor accessor) {
        String targetId = strOrNull(payload, "targetId");
        String response = strOrNull(payload, "response"); // OPEN | DESTROY | MINIMIZE
        if (targetId == null || response == null) return;
        String senderId = senderId(accessor);
        if (senderId == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("type", "INVITE_RESPONSE");
        body.put("senderId", senderId);
        body.put("response", response);
        body.put("ts", System.currentTimeMillis());

        forwardToPlayer(targetId, WebSocketConfig.QUEUE_PRIVATE_INVITE, body);
    }

    private void forwardToPlayer(String playerId, String destination, Object body) {
        Player p = room().findById(playerId).orElse(null);
        if (p == null || p.getIp() == null) {
            log.debug("[PrivateChat] no player or ip for {}", playerId);
            return;
        }
        Set<String> targets = sessions.getSessionsByIp(p.getIp());
        if (targets.isEmpty()) return;
        for (String sid : targets) {
            SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create();
            h.setSessionId(sid);
            h.setLeaveMutable(true);
            messaging.convertAndSendToUser(sid, destination, body, h.getMessageHeaders());
        }
    }

    private String senderId(SimpMessageHeaderAccessor accessor) {
        String ip = sessions.getIpBySession(accessor.getSessionId());
        if (ip == null) return null;
        return room().findByIp(ip).map(Player::getId).orElse(null);
    }

    private String nameOf(String playerId) {
        return room().findById(playerId).map(Player::getName).orElse("用户");
    }

    private static String strOrNull(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
