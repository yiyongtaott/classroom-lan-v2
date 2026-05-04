package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.lanclassroom.net.ws.ClientSessionRegistry;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 谁是卧底游戏逻辑。
 */
@Component
public class SpyGame implements GameSession {
    private final SimpMessagingTemplate messaging;
    private final ClientSessionRegistry sessions;
    private Room room;
    private Broadcaster broadcaster;
    private String spyWord;
    private String civilianWord;
    private String spyPlayerId;
    private final Map<String, Integer> votes = new HashMap<>();
    private boolean gameOver = false;
    private String stage = "WAITING"; // WAITING | START | VOTING | RESULT

    public SpyGame(SimpMessagingTemplate messaging, ClientSessionRegistry sessions) {
        this.messaging = messaging;
        this.sessions = sessions;
    }

    @Override
    public GameType getType() { return GameType.SPY; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.gameOver = false;
        this.votes.clear();
        this.stage = "START";

        List<Player> players = new ArrayList<>(room.getPlayers());
        if (players.size() < 3) return; // 至少3人

        Collections.shuffle(players);
        spyPlayerId = players.get(0).getId();

        // 简易词库
        String[][] wordPairs = {
            {"苹果", "梨子"},
            {"手机", "座机"},
            {"键盘", "鼠标"}
        };
        String[] pair = wordPairs[ThreadLocalRandom.current().nextInt(wordPairs.length)];
        civilianWord = pair[0];
        spyWord = pair[1];

        // 实际场景应发送私信，此处广播状态，前端根据角色ID显示
        for (Player p : players) {
            sendPrivate(p.getId(), Map.of(
                    "type", "WORD_REVEAL",
                    "word", p.getId().equals(spyPlayerId) ? spyWord : civilianWord
            ));
        }
        broadcastState();
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        String action = (String) payload.get("action");
        if ("VOTE".equals(action)) {
            String targetId = (String) payload.get("targetId");
            votes.put(player.getId(), Integer.parseInt(targetId));

            if (votes.size() == room.getPlayers().size()) {
                calculateResult();
            }
        } else if ("TO_VOTING".equals(action)) {
            this.stage = "VOTING";
            broadcastState();
        }
    }

    private void calculateResult() {
        this.stage = "RESULT";
        this.gameOver = true;
        // 简单结算：投票最多的为卧底则平民赢
        broadcastState();
    }

    private void broadcastState() {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("gameOver", gameOver);
        state.put("players", room.getPlayers());
        broadcaster.broadcast(state);
    }

    private void sendPrivate(String playerId, Map<String, Object> body) {
        if (room == null) return;
        Player p = room.findById(playerId).orElse(null);
        if (p == null || p.getIp() == null) return;
        for (String sid : sessions.getSessionsByIp(p.getIp())) {
            SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create();
            h.setSessionId(sid);
            h.setLeaveMutable(true);
            messaging.convertAndSendToUser(sid, WebSocketConfig.QUEUE_SPY_PRIVATE, body, h.getMessageHeaders());
        }
    }

    @Override
    public void stop() {
        this.stage = "WAITING";
    }
}
