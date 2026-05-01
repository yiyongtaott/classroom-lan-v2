package org.lanclassroom.net.api;

import jakarta.servlet.http.HttpServletRequest;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.model.RoomSnapshot;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.discovery.HostElector;
import org.lanclassroom.net.service.GameEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * 房间 / 节点状态 REST API。
 *
 * 关键语义（Bug 2）：/api/status 返回的是"访问者视角"。
 *   - nodeId    = 访问者本机 IP（HTTP 请求的来源 IP）
 *   - host      = 访问者是否就是 Host 自己（同 IP 或 loopback）
 *   - hostNodeId / hostHostname 始终代表当前 Host
 *
 * 这样 Host 自己用浏览器访问看到的是 HOST，
 * 其他人访问 Host 提供的同一页面看到的是 CLIENT 且 nodeId 是自己机器的 IP。
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final HostElector elector;
    private final Room room;
    private final GameEngine engine;

    public RoomController(HostElector elector, Room room, GameEngine engine) {
        this.elector = elector;
        this.room = room;
        this.engine = engine;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest req) {
        String hostIp = NodeIdGenerator.getNodeId();
        String accessorIp = resolveClientIp(req);
        boolean accessorIsHost = isAccessorTheHost(accessorIp, hostIp);

        Map<String, Object> result = new HashMap<>();
        // 访问者视角
        result.put("nodeId", accessorIsHost ? hostIp : accessorIp);
        result.put("hostname", accessorIsHost ? NodeIdGenerator.getHostname() : null);
        result.put("host", accessorIsHost);
        // Host 元信息（共享）
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

    @PostMapping("/room/players")
    public ResponseEntity<Player> joinRoom(@RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        String name = body == null ? null : body.get("name");
        String hostname = body == null ? null : body.get("hostname");
        String avatar = body == null ? null : body.get("avatar");
        String accessorIp = resolveClientIp(req);

        // 显示用 IP：访问者是 Host 时取本机 IP，否则取 HTTP 来源 IP
        String displayIp = isAccessorTheHost(accessorIp, NodeIdGenerator.getNodeId())
                ? NodeIdGenerator.getNodeId()
                : accessorIp;

        // 默认昵称：客户端 hostname / IP / "玩家"
        String finalName = (name != null && !name.isBlank())
                ? name
                : (hostname != null && !hostname.isBlank()
                    ? hostname
                    : (displayIp != null ? displayIp : "玩家"));

        Player player = new Player(finalName)
                .setIp(displayIp)
                .setHostname(hostname)
                .setAvatar(avatar);
        room.addPlayer(player);
        return ResponseEntity.ok(player);
    }

    @DeleteMapping("/room/players/{id}")
    public ResponseEntity<Void> leaveRoom(@PathVariable("id") String id) {
        room.removePlayerById(id);
        return ResponseEntity.noContent().build();
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

    /** 访问者是否就是 Host 自己（同 IP / loopback）。 */
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
