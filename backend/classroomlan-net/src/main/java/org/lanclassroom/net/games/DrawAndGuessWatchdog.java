package org.lanclassroom.net.games;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 你画我猜回合超时巡检器 - 每秒触发一次 watchdog。
 */
@Component
public class DrawAndGuessWatchdog {

    private final DrawAndGuessGame game;

    public DrawAndGuessWatchdog(DrawAndGuessGame game) {
        this.game = game;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        game.watchdog();
    }
}
