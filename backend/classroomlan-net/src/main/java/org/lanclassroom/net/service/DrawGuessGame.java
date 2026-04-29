package org.lanclassroom.net.service;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.GameSession;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 你画我猜游戏实现
 */
public class DrawGuessGame implements GameSession {

    private Room room;
    private boolean running = false;

    // 词库
    private static final String[] WORDS = {
        "苹果", "香蕉", "西瓜", "房子", "汽车", "飞机",
        "猫", "狗", "太阳", "月亮", "树", "花", "电脑", "手机"
    };

    private String currentWord;
    private String drawerId;
    private int round;

    @Override
    public void start(Room room) {
        this.room = room;
        this.running = true;
        this.round = 1;
        this.currentWord = WORDS[ThreadLocalRandom.current().nextInt(WORDS.length)];

        // 随机指定一名玩家为画手（简单实现：取第一个玩家）
        if (!room.getPlayers().isEmpty()) {
            this.drawerId = room.getPlayers().get(0).getId();
        }

        System.out.println("[DrawGuess] 游戏开始，第 " + round + " 轮，当前词语：" + currentWord);
        // TODO: 通过 WebSocket 发送游戏状态到前端
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running) return;

        String action = (String) payload.get("action");
        if (action == null) return;

        switch (action) {
            case "DRAW":
                // 转发绘图数据（坐标点、颜色等）到前端
                // broadcastDrawData(payload);
                break;
            case "GUESS":
                String guess = (String) payload.get("guess");
                if (guess != null && guess.equals(currentWord)) {
                    System.out.println("猜对了！" + player.getName());
                    // 结束本轮，开始下一轮
                    nextRound();
                } else {
                    System.out.println(player.getName() + " 猜错了：" + guess);
                }
                break;
            case "SKIP":
                nextRound();
                break;
            default:
                System.out.println("[DrawGuess] Unknown action: " + action);
        }
    }

    @Override
    public void stop() {
        running = false;
        System.out.println("[DrawGuess] 游戏结束");
    }

    @Override
    public GameType getType() {
        return GameType.DRAW;
    }

    private void nextRound() {
        round++;
        currentWord = WORDS[ThreadLocalRandom.current().nextInt(WORDS.length)];
        // 换画手
        int idx = room.getPlayers().indexOf(room.getPlayers().stream()
                .filter(p -> p.getId().equals(drawerId)).findFirst().orElse(null));
        if (idx >= 0) {
            drawerId = room.getPlayers().get((idx + 1) % room.getPlayers().size()).getId();
        }
        System.out.println("[DrawGuess] 第 " + round + " 轮，当前词语：" + currentWord);
    }
}
