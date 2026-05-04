package org.lanclassroom.net.api;

import jakarta.servlet.http.HttpServletRequest;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.model.RoomSnapshot;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.discovery.HostElector;
import org.lanclassroom.net.service.AvatarStorageService;
import org.lanclassroom.net.service.GameEngine;
import org.lanclassroom.net.service.StatusBroadcastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;

/**
 * 房间 / 玩家 REST 接口。
 *
 * 注意：
 *   - GET /api/status 与 GET /api/room 保留供调试，但前端正常运行时不再轮询；
 *     前端走 /user/queue/init（连接握手快照） + /topic/status + /topic/room 增量。
 *   - 玩家加入 / 改名 / 改头像后必定 broadcast 状态 + user.update（BUG-02）。
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoomController.class);

    private final HostElector elector;
    private final org.lanclassroom.core.service.RoomManager roomManager;
    private final GameEngine engine;
    private final DiscoveryService discovery;
    private final AvatarStorageService avatars;
    private final StatusBroadcastService statusBroadcast;

    public RoomController(HostElector elector, org.lanclassroom.core.service.RoomManager roomManager, GameEngine engine,
                          DiscoveryService discovery, AvatarStorageService avatars,
                          StatusBroadcastService statusBroadcast) {
        this.elector = elector;
        this.roomManager = roomManager;
        this.engine = engine;
        this.discovery = discovery;
        this.avatars = avatars;
        this.statusBroadcast = statusBroadcast;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest req) {
        log.debug("[房间接口] 查询状态 IP={}", resolveDisplayIp(req));
        String accessorIp = resolveDisplayIp(req);
        return ResponseEntity.ok(statusBroadcast.buildStatusFor(accessorIp));
    }

    @GetMapping("/room")
    public ResponseEntity<RoomSnapshot> snapshotDefault() {
        return snapshot("default");
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<RoomSnapshot> snapshot(@PathVariable String roomId) {
        Room room = roomManager.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        room.setHostNodeId(elector.getHostId());
        return ResponseEntity.ok(room.snapshot());
    }

    /**
     * Bug 4: 通过来源 IP 查"本机已有玩家"。
     * 用于无痕浏览器打开时不重复创建账号。
     */
    @GetMapping("/me")
    public ResponseEntity<Player> me(HttpServletRequest req) {
        String ip = resolveDisplayIp(req);

        // 查找所有房间中的玩家
        for (Room room : roomManager.getAllRooms().values()) {
            Player p = room.findByIp(ip).orElse(null);
            if (p != null) return ResponseEntity.ok(p);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 加入 / 重连 / 改名 / 改头像统一入口。
     * Bug 4: 同 IP 已有 player → 直接返回（可选更新 name）。
     * BUG-02: 改名后必广播 user.update。
     */
    @PostMapping("/room/{roomId}/players")
    public ResponseEntity<Player> joinRoom(@PathVariable String roomId,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        Room room = roomManager.getRoom(roomId);
        if (room == null) room = roomManager.createRoom(roomId);

        String deviceId = body == null ? null : body.get("deviceId");
        String name = body == null ? null : body.get("name");
        String hostname = body == null ? null : body.get("hostname");
        String accessorIp = resolveDisplayIp(req);

        // 优先使用 deviceId 识别（如果客户端支持）
        Player existing = null;
        if (deviceId != null && !deviceId.isBlank()) {
            existing = room.findById(deviceId).orElse(null);
        }

        // 兼容旧逻辑：IP 查找
        if (existing == null) {
            existing = room.findByIp(accessorIp).orElse(null);
        }

        if (existing != null) {
            boolean changed = false;
            // 更新 ID 逻辑
            if (deviceId != null && !deviceId.equals(existing.getId())) {
                 existing.setId(deviceId); // 强制绑定新 ID
            }
            if (name != null && !name.isBlank() && !name.equals(existing.getName())) {
                avatars.renameUser(existing.getName(), name.trim());
                existing.setName(name.trim());
                changed = true;
            }
            if (hostname != null && !hostname.isBlank() && !hostname.equals(existing.getHostname())) {
                existing.setHostname(hostname);
                changed = true;
            }
            // 用最新头像（按用户名查）
            String currentAvatar = existing.getAvatar();
            String avatarUrl = avatars.avatarUrlOf(existing.getName()).orElse(null);
            if (avatarUrl != null && !avatarUrl.equals(currentAvatar)) {
                existing.setAvatar(avatarUrl);
            }
            if (changed) {
                statusBroadcast.broadcastUserUpdate(existing);
                statusBroadcast.broadcastRoom();
            }
            return ResponseEntity.ok(existing);
        }

        // 默认昵称：客户端 hostname / IP
        String finalName = (name != null && !name.isBlank())
                ? name.trim()
                : (hostname != null && !hostname.isBlank()
                    ? hostname
                    : (accessorIp != null ? accessorIp : "玩家"));

        String finalId = (deviceId != null && !deviceId.isBlank()) ? deviceId : java.util.UUID.randomUUID().toString();
        Player player = new Player(finalName).setId(finalId)
                .setIp(accessorIp)
                .setHostname(hostname);

        // 已有头像 map 命中则恢复
        String avatarUrl = avatars.avatarUrlOf(finalName).orElse(null);
        if (avatarUrl != null) player.setAvatar(avatarUrl);

        room.addPlayer(player);
        statusBroadcast.broadcastRoom();
        statusBroadcast.broadcastStatus();
        return ResponseEntity.ok(player);
    }

    @PostMapping("/room/players")
    public ResponseEntity<Player> joinRoomDefault(@RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        return joinRoom("default", body, req);
    }

    @DeleteMapping("/room/{roomId}/players/{id}")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, @PathVariable String id) {
        Room room = roomManager.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        if (room.removePlayerById(id)) {
            statusBroadcast.broadcastRoom();
            statusBroadcast.broadcastStatus();
        }
        return ResponseEntity.noContent().build();
    }

    /** BUG-02：改名 / 改头像后必广播 user.update。 */
    @PutMapping("/room/{roomId}/players/{id}")
    public ResponseEntity<Player> updatePlayer(@PathVariable String roomId,
                                               @PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        Room room = roomManager.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        Player p = room.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        boolean changed = false;
        if (body != null) {
            String name = body.get("name");
            if (name != null && !name.isBlank() && !name.equals(p.getName())) {
                avatars.renameUser(p.getName(), name.trim());
                p.setName(name.trim());
                avatars.avatarUrlOf(p.getName()).ifPresent(p::setAvatar);
                changed = true;
            }
            String avatar = body.get("avatar");
            if (avatar != null) {
                if (avatar.isBlank()) {
                    avatars.clearFor(p.getName());
                    p.setAvatar(null);
                } else {
                    p.setAvatar(avatar);
                }
                changed = true;
            }
        }
        if (changed) {
            statusBroadcast.broadcastUserUpdate(p);
            statusBroadcast.broadcastRoom();
        }
        return ResponseEntity.ok(p);
    }

    @GetMapping("/room/{roomId}/games")
    public ResponseEntity<Map<String, Object>> listGames(@PathVariable String roomId) {
        Room room = roomManager.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "registered", engine.registeredGames().keySet(),
                "active", room.getGameType()
        ));
    }

    private static String resolveClientIp(HttpServletRequest req) {
        if (req == null) return null;
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** 用于展示的 IP：把 loopback 折叠为 host 自己的 LAN IP，让 host 自己也归在唯一身份下。 */
    private String resolveDisplayIp(HttpServletRequest req) {
        String raw = resolveClientIp(req);
        if (raw == null) return null;
        if (isAccessorTheHost(raw, NodeIdGenerator.getNodeId())) {
            return NodeIdGenerator.getNodeId();
        }
        return raw;
    }

    private static boolean isAccessorTheHost(String accessorIp, String hostIp) {
        if (accessorIp == null) return false;
        if (accessorIp.equals(hostIp)) return true;
        try {
            return InetAddress.getByName(accessorIp).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
