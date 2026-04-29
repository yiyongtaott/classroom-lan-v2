package org.lanclassroom.core.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;

import java.util.Map;

/**
 * 游戏会话接口 - 定义游戏生命周期
 * 具体游戏（你画我猜、狼人杀等）需实现此接口作为 Spring Bean 注册
 */
public interface GameSession {

    /**
     * 启动游戏
     * @param room 房间对象
     */
    void start(Room room);

    /**
     * 处理玩家动作
     * @param player 操作玩家
     * @param payload 动作载荷（游戏相关参数）
     */
    void handleAction(Player player, Map<String, Object> payload);

    /**
     * 停止游戏
     */
    void stop();

    /**
     * 获取游戏类型
     * @return GameType 枚举值
     */
    GameType getType();
}
