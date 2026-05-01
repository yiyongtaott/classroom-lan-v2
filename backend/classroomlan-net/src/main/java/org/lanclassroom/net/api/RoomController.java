package org.lanclassroom.net.api;

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

    /** 节点状态 - 客户端启动后用它判断本机角色。 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", NodeIdGenerator.getNodeId());
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
    public ResponseEntity<Player> joinRoom(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "玩家");
        Player player = new Player(name);
        room.addPlayer(player);
        return ResponseEntity.ok(player);
    }

    @DeleteMapping("/room/players/{id}")
    public ResponseEntity<Void> leaveRoom(@PathVariable String id) {
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
}
