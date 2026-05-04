package org.lanclassroom.core.service;

import org.lanclassroom.core.model.Room;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间管理器 - 单 Host 模型下的多房间容器。
 * (移除 @Service 以避免核心模块强制依赖 Spring，改为在 AppConfig 中手动配置或在 App 模块中注册)
 */
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String roomId) {
        Room room = new Room();
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Map<String, Room> getAllRooms() {
        return rooms;
    }
}
