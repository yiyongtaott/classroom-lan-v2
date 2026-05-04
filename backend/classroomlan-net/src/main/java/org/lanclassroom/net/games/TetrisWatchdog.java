package org.lanclassroom.net.games;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TetrisWatchdog {
    private final TetrisGame game;

    public TetrisWatchdog(TetrisGame game) {
        this.game = game;
    }

    @Scheduled(fixedRate = 800)
    public void tick() {
        game.watchdog();
    }
}
