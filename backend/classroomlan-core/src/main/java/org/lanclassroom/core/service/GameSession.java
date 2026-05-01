package org.lanclassroom.core.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;

import java.util.Map;

/**
 * 游戏会话抽象 - 单一游戏的生命周期。
 * 扩展新游戏只需实现该接口并注册为 Spring Bean。
 *
 * 调用顺序约定：
 *   1. start(room, broadcaster)  - 进入游戏
 *   2. handleAction(player, ...) - 处理客户端动作（可重复）
 *   3. stop()                    - 结束游戏
 *
 * 实现需保证 handleAction 在 stop 之后调用为 no-op。
 */
public interface GameSession {

    /** 游戏类型 - 注册键 */
    GameType getType();

    /**
     * 启动游戏。GameSession 应保存 broadcaster 以便后续广播状态。
     */
    void start(Room room, Broadcaster broadcaster);

    /**
     * 处理玩家动作。payload 由具体游戏定义结构。
     */
    void handleAction(Player player, Map<String, Object> payload);

    /** 结束游戏，释放资源。 */
    void stop();
}
