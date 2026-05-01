package org.lanclassroom.core.service;

/**
 * 状态广播器 - 让 GameSession 能向客户端推送状态而不依赖 Spring Messaging。
 * net 层提供 SimpMessagingTemplate 适配器实现，core 层不知道具体协议。
 */
@FunctionalInterface
public interface Broadcaster {
    /**
     * 广播状态对象到所有订阅者（实现负责序列化 + 选择 topic）。
     */
    void broadcast(Object state);
}
