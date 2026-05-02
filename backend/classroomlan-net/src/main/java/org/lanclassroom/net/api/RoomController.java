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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RoomController {

    private final HostElector elector;
    private final Room room;
    private final GameEngine engine;
    private final DiscoveryService discovery;
    private final AvatarStorageService avatars;

    public RoomController(HostElector elector, Room room, GameEngine engine,
                          DiscoveryService discovery, AvatarStorageService avatars) {
        this.elector = elector;
        this.room = room;
        this.engine = engine;
        this.discovery = discovery;
        this.avatars = avatars;
    }

    /**
     * 节点状态 - 访问者视角。
     * Bug 10: 即使访问者不是 Host，也尝试通过 IP 反查它本机后端通报的 hostname。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest req) {
        String hostIp = NodeIdGenerator.getNodeId();
        String accessorIp = resolveClientIp(req);
        boolean accessorIsHost = isAccessorTheHost(accessorIp, hostIp);

        String accessorHostname = accessorIsHost
                ? NodeIdGenerator.getHostname()
                : discovery.hostnameByIp(accessorIp);

        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", accessorIsHost ? hostIp : accessorIp);
        result.put("hostname", accessorHostname);
        result.put("host", accessorIsHost);
        result.put("hostNodeId", elector.electHost());
        result.put("hostHostname", NodeIdGenerator.getHostname());
        result.put("peerCount", elector.peerCount());
        result.put("playerCount", room.getPlayers().size());
        result.put("gameType", room.getGameType());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/room")
    public ResponseEntity<RoomSnapshot> snapshot() {
        room.setHostNodeId(elector.electHost());
        return ResponseEntity.ok(room.snapshot());
    }

    /**
     * Bug 4: 通过来源 IP 查"本机已有玩家"。
     * 用于无痕浏览器打开时不重复创建账号。
     */
    @GetMapping("/me")
    public ResponseEntity<Player> me(HttpServletRequest req) {
        String ip = resolveDisplayIp(req);
        return room.findByIp(ip)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 加入 / 重连 / 改名 / 改头像统一入口。
     * Bug 4: 同 IP 已有 player → 直接返回（可选更新 name）。
     */
    @PostMapping("/room/players")
    public ResponseEntity<Player> joinRoom(@RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        String name = body == null ? null : body.get("name");
        String hostname = body == null ? null : body.get("hostname");
        String accessorIp = resolveDisplayIp(req);

        // 同 IP 已有玩家 → 复用
        Player existing = room.findByIp(accessorIp).orElse(null);
        if (existing != null) {
            if (name != null && !name.isBlank() && !name.equals(existing.getName())) {
                avatars.renameUser(existing.getName(), name.trim());
                existing.setName(name.trim());
            }
            if (hostname != null && !hostname.isBlank()) {
                existing.setHostname(hostname);
            }
            // 用最新头像（按用户名查）
            avatars.avatarUrlOf(existing.getName()).ifPresent(existing::setAvatar);
            return ResponseEntity.ok(existing);
        }

        // 默认昵称：客户端 hostname / IP
        String finalName = (name != null && !name.isBlank())
                ? name.trim()
                : (hostname != null && !hostname.isBlank()
                    ? hostname
                    : (accessorIp != null ? accessorIp : "玩家"));

        Player player = new Player(finalName)
                .setIp(accessorIp)
                .setHostname(hostname);

        // 已有头像 map 命中则恢复
        avatars.avatarUrlOf(finalName).ifPresent(player::setAvatar);

        room.addPlayer(player);
        return ResponseEntity.ok(player);
    }

    @DeleteMapping("/room/players/{id}")
    public ResponseEntity<Void> leaveRoom(@PathVariable("id") String id) {
        room.removePlayerById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/room/players/{id}")
    public ResponseEntity<Player> updatePlayer(@PathVariable("id") String id,
                                               @RequestBody Map<String, String> body) {
        Player p = room.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        if (body != null) {
            String name = body.get("name");
            if (name != null && !name.isBlank() && !name.equals(p.getName())) {
                avatars.renameUser(p.getName(), name.trim());
                p.setName(name.trim());
                // 重新查 avatar 绑定
                avatars.avatarUrlOf(p.getName()).ifPresent(p::setAvatar);
            }
            String avatar = body.get("avatar");
            if (avatar != null) {
                if (avatar.isBlank()) {
                    avatars.clearFor(p.getName());
                    p.setAvatar(null);
                } else {
                    p.setAvatar(avatar);
                }
            }
        }
        return ResponseEntity.ok(p);
    }

    @GetMapping("/games")
    public ResponseEntity<Map<String, Object>> listGames() {
        Map<String, Object> result = new HashMap<>();
        result.put("registered", engine.registeredGames().keySet());
        result.put("active", room.getGameType());
        return ResponseEntity.ok(result);
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
