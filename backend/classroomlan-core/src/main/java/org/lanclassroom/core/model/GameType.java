package org.lanclassroom.core.model;

/**
 * 游戏类型枚举。新增游戏只需追加一个枚举值并实现对应 GameSession。
 */
public enum GameType {
    NUMBER_GUESS,   // 猜数字（参考实现）
    DRAW,           // 你画我猜（预留）
    QUIZ,           // 抢答（预留）
    CUSTOM          // 用户自定义
}
