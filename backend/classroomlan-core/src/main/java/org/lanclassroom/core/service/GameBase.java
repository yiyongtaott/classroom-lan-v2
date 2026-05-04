package org.lanclassroom.core.service;

import org.lanclassroom.core.model.Player;
import java.util.List;

public interface GameBase {
    String getName();
    void onPlayerAction(Player player, Object action);
    void startGame(List<Player> players);
    void stopGame();
}
