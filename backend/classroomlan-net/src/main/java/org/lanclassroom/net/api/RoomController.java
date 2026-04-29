package org.lanclassroom.net.api;

import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.model.RoomSnapshot;
import org.lanclassroom.net.discovery.HostElector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API 控制器 - 提供房间查询、快照等端点
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final HostElector hostElector;
    private Room currentRoom;

    public RoomController(HostElector hostElector) {
        this.hostElector = hostElector;
    }

    /**
     * 获取当前 Host 状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("host", hostElector.isHost());
        result.put("nodeId", hostElector.getNodeId());
        if (hostElector.isHost() && currentRoom != null) {
            result.put("roomKey", currentRoom.getRoomKey());
            result.put("playerCount", currentRoom.getPlayers().size());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取房间完整快照（客户端重连用）
     */
    @GetMapping("/room/snapshot")
    public ResponseEntity<RoomSnapshot> snapshot(
        @RequestParam("key") String roomKey,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        if (currentRoom == null || !currentRoom.getRoomKey().equals(roomKey)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(currentRoom.snapshot());
    }
}
