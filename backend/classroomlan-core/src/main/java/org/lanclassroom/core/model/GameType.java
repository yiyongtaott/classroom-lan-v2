package org.lanclassroom.core.model;

/**
 * 游戏类型枚举。新增游戏只需追加一个枚举值并实现对应 GameSession。
 */
public enum GameType {
    NUMBER_GUESS,   // 猜数字（参考实现）
    DRAW,           // 你画我猜（预留）
    QUIZ,           // 抢答（预留）
    MINESWEEPER,    // 扫雷
    TETRIS,         // 俄罗斯方块
    FIVE_IN_A_ROW,  // 五子棋
    GAME_2048,      // 2048
    GOLD_MINER,     // 黄金矿工
    SNAKE,          // 贪吃蛇
    FLAPPY_BIRD,    // 像素鸟
    BREAKOUT,       // 打砖块
    SUDOKU,         // 数独
    SPY,            // 谁是卧底
    TANK_TROUBLE,   // 坦克大战
    FIGHT_LANDLORD, // 斗地主
    BOMBERMAN,      // 炸弹人
    FLYING_CHESS,   // 飞行棋
    UNO,            // UNO
    CUSTOM          // 用户自定义
}
