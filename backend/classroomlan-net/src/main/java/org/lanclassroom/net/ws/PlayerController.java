package org.lanclassroom.net.ws;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.net.service.ConnectionTracker;
import org.lanclassroom.net.service.UserStatusService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

import static org.lanclassroom.net.discovery.DiscoveryService.log;

/**
 * 玩家在线状态 STOMP 控制器。
 * 客户端 connect 上 /ws 后必须发送 /app/player.online {playerId} 完成绑定。
 *
 * 同时承载 /app/user.page-active —— 标签页 visibilityState 上报（任务 3 第三圆）。
 */
@Controller
public class PlayerController {

    private final ConnectionTracker tracker;
    private final ClientSessionRegistry registry;
    private final UserStatusService userStatus;
    private final RoomManager roomManager;
    private final org.lanclassroom.net.service.StatusBroadcastService statusBroadcast;

    public PlayerController(ConnectionTracker tracker,
                            ClientSessionRegistry registry,
                            UserStatusService userStatus,
                            RoomManager roomManager,
                            org.lanclassroom.net.service.StatusBroadcastService statusBroadcast) {
        this.tracker = tracker;
        this.registry = registry;
        this.userStatus = userStatus;
        this.roomManager = roomManager;
        this.statusBroadcast = statusBroadcast;
    }

    @MessageMapping("/player.online")
    public void online(@Payload Map<String, String> payload,
                       SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String playerId = payload == null ? null : payload.get("playerId");
        tracker.bind(sessionId, playerId);
    }

    @MessageMapping("/user.update")
    public void updateProfile(@Payload Map<String, String> payload,
                              SimpMessageHeaderAccessor accessor) {
        String sid = accessor.getSessionId();
        String playerId = tracker.getPlayerIdBySession(sid);
        Player p = playerId == null ? null : room().findById(playerId).orElse(null);
        if (p == null) {
            String ip = registry.getIpBySession(sid);
            if (ip == null) return;
            p = room().findByIp(ip).orElse(null);
        }
        if (p == null) return;

        String newName = payload.get("name");
        if (newName != null && !newName.isBlank()) {
            p.setName(newName);
        }
        String newAvatar = payload.get("avatar");
        if (newAvatar != null) {
            p.setAvatar(newAvatar);
        }

        // 触发广播同步
        statusBroadcast.broadcastUserUpdate(p);
        log.info("[PlayerController] 用户 {} 更新资料: name={}, avatar={}", p.getId(), newName, newAvatar);
    }

    /**
     * 任务 3：客户端上报 visibilityState 是否为 visible。

     * 服务端按 session → ip → player → userId 路径定位用户。
     */
    @MessageMapping("/user.page-active")
    public void pageActive(@Payload Map<String, Object> payload,
                           SimpMessageHeaderAccessor accessor) {
        if (payload == null) return;
        Object active = payload.get("active");
        if (!(active instanceof Boolean)) return;

        String sid = accessor.getSessionId();
        String playerId = tracker.getPlayerIdBySession(sid);
        Player p = playerId == null ? null : room().findById(playerId).orElse(null);
        if (p == null) {
            String ip = registry.getIpBySession(sid);
            if (ip == null) return;
            p = room().findByIp(ip).orElse(null);
        }
        if (p == null) return;
//        log.info("[PAGE_ACTIVE] playerId={} active={} 写入后 status={}", p.getId(), active, userStatus);
        userStatus.setPageActive(p.getId(), (Boolean) active);
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
