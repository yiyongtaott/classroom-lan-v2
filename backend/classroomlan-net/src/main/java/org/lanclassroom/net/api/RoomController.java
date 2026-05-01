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

import java.util.HashMap;
import java.util.Map;

/**
 * 房间 / 节点状态 REST API。
 *
 * 重要变更（Bug 3）：
 *   - status 同时返回 nodeId（=本机 IP）与 hostname（系统名），客户端用作默认昵称
 *   - 加入房间时由后端回填 player 的 ip / hostname / 默认 name（如未提供）
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
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", NodeIdGenerator.getNodeId());
        result.put("hostname", NodeIdGenerator.getHostname());
        result.put("host", elector.isHost());
        result.put("hostNodeId", elector.electHost());
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
        String clientIp = resolveClientIp(req);

        // 默认昵称：客户端 hostname / 客户端 IP / "玩家"
        String finalName = (name != null && !name.isBlank())
                ? name
                : (hostname != null && !hostname.isBlank()
                    ? hostname
                    : (clientIp != null ? clientIp : "玩家"));

        Player player = new Player(finalName)
                .setIp(clientIp)
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
}
