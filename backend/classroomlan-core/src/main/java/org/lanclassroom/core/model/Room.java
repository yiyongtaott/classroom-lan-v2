package org.lanclassroom.core.model;

import lombok.Data;
import lombok.Getter;
import org.lanclassroom.core.service.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 房间实体 - Host 节点上的全局状态容器。
 * 单 Host 模型 → 整个进程内只持有一个 Room 实例。
 */
@Data
public class Room {

    private String hostNodeId;
    private GameType gameType;
    private GameSession gameSession;
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private long createdAt = System.currentTimeMillis();

    public RoomSnapshot snapshot() {
        RoomSnapshot snap = new RoomSnapshot();
        snap.setHostNodeId(hostNodeId);
        snap.setGameType(gameType);
        snap.setPlayers(new ArrayList<>(players));
        snap.setPlayerCount(players.size());
        return snap;
    }

    public Player addPlayer(Player p) {
        players.add(p);
        return p;
    }

    public boolean removePlayerById(String playerId) {
        return players.removeIf(p -> p.getId().equals(playerId));
    }

    public Optional<Player> findById(String playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst();
    }

    public Optional<Player> findByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return Optional.empty();
        return players.stream().filter(p -> deviceId.equals(p.getDeviceId())).findFirst();
    }

    public Optional<Player> findByIp(String ip) {
        if (ip == null) return Optional.empty();
        return players.stream().filter(p -> ip.equals(p.getIp())).findFirst();
    }

    public Optional<Player> findByName(String name) {
        if (name == null) return Optional.empty();
        return players.stream().filter(p -> name.equals(p.getName())).findFirst();
    }

    public Room setHostNodeId(String hostNodeId) { this.hostNodeId = hostNodeId; return this; }

    public Room setGameType(GameType gameType) { this.gameType = gameType; return this; }

    public Room setGameSession(GameSession gameSession) { this.gameSession = gameSession; return this; }

    public Room setCreatedAt(long createdAt) { this.createdAt = createdAt; return this; }
}
