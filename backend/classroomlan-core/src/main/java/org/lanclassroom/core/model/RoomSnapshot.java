package org.lanclassroom.core.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 房间快照 - 客户端连接 / 重连时拉取的完整视图。
 */
@Data
@Component
public class RoomSnapshot {
    private String hostNodeId;
    private GameType gameType;
    private List<Player> players;
    private int playerCount;

}
