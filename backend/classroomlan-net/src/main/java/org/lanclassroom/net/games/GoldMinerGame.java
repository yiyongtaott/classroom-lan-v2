package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GoldMinerGame implements GameSession {
    private Room room;
    private Broadcaster broadcaster;
    private int score = 0;
    private int timeRemaining = 60; // 倒计时 60s
    private boolean gameOver = false;
    private List<Item> items = new ArrayList<>();
    private ScheduledExecutorService timer;

    @Override
    public GameType getType() { return GameType.GOLD_MINER; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.score = 0;
        this.timeRemaining = 60;
        this.gameOver = false;

        // 生成矿石物品池
        items.clear();
        Random rand = new Random();
        for(int i=0; i<10; i++) {
            items.add(new Item(rand.nextInt(500), rand.nextInt(300), rand.nextInt(100) + 50));
        }

        startTimer();
        broadcastState("START");
    }

    private void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            if (timeRemaining > 0) {
                timeRemaining--;
                broadcastState("TICK");
            } else {
                gameOver = true;
                stopTimer();
                broadcastState("OVER");
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timer != null) timer.shutdownNow();
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (gameOver) return;
        String action = (String) payload.get("action");
        if ("GRAB".equals(action)) {
            // 简单的抓取逻辑：判定是否抓到最近的矿石
            Iterator<Item> iterator = items.iterator();
            while (iterator.hasNext()) {
                Item item = iterator.next();
                // 模拟简单的距离判定
                if (item.y > 200) { // 模拟爪子位置
                    score += item.value;
                    iterator.remove();
                    break;
                }
            }
            broadcastState("UPDATE");
        }
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("score", score);
        state.put("time", timeRemaining);
        state.put("items", items);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);
    }

    @Override
    public void stop() {
        stopTimer();
    }

    public static class Item {
        public int x, y, value;
        Item(int x, int y, int v) { this.x=x; this.y=y; this.value=v; }
    }
}
