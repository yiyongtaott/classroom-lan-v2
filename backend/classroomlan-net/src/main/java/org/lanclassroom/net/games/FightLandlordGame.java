package org.lanclassroom.net.games;

import org.lanclassroom.core.model.GameType;
import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.core.service.GameSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.lanclassroom.net.ws.ClientSessionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class FightLandlordGame implements GameSession {
    private Room room;
    private Broadcaster broadcaster;
    private final SimpMessagingTemplate messaging;
    private final ClientSessionRegistry sessions;
    private volatile boolean running;
    private boolean gameOver = false;
    private String stage = "WAITING"; // WAITING | BIDDING | PLAYING | RESULT

    private String landlordId;
    private final Map<String, List<Integer>> playerHands = new ConcurrentHashMap<>();
    private String currentTurnPlayerId;
    private List<Integer> lastPlayedCards = new ArrayList<>();
    private String lastPlayedBy;

    public FightLandlordGame(SimpMessagingTemplate messaging, ClientSessionRegistry sessions) {
        this.messaging = messaging;
        this.sessions = sessions;
    }

    @Override
    public GameType getType() { return GameType.FIGHT_LANDLORD; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.stage = "BIDDING";
        this.landlordId = null;
        this.playerHands.clear();
        broadcastState();
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        String action = (String) payload.get("action");
        if (action == null) return;

        switch (action) {
            case "BID" -> handleBid(player);
            case "PASS" -> handlePass(player);
            case "PLAY" -> handlePlay(player, payload);
        }
    }

    private void handleBid(Player player) {
        if (!"BIDDING".equals(stage)) return;
        this.landlordId = player.getId();
        this.stage = "PLAYING";
        dealCards();
        broadcastState();
    }

    private void handlePass(Player player) {
        if (!"BIDDING".equals(stage)) return;
        // Simple bidding: if everyone passes, random landlord
        // For this version, first one to bid wins.
    }

    private void dealCards() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 3; i <= 14; i++) { // 3-10, J, Q, K, A, 2
            for (int j = 0; j < 4; j++) deck.add(i);
        }
        Collections.shuffle(deck);

        List<String> onlinePlayers = room.getPlayers().stream()
                .filter(p -> Player.STATUS_ONLINE.equals(p.getStatus()))
                .map(Player::getId)
                .toList();

        if (onlinePlayers.size() < 3) {
            // For testing/small rooms, we just distribute whatever we have
            // In real production, we'd enforce 3 players
        }

        int cardsPerPerson = deck.size() / onlinePlayers.size();
        for (int i = 0; i < onlinePlayers.size(); i++) {
            String pid = onlinePlayers.get(i);
            List<Integer> hand = new ArrayList<>(deck.subList(i * cardsPerPerson, (i + 1) * cardsPerPerson));
            Collections.sort(hand);
            playerHands.put(pid, hand);
        }

        // Landlord gets extra cards (simplified)
        // In real FightLandlord, 3 cards are shared. We'll skip the shared cards for simplicity.

        this.currentTurnPlayerId = landlordId;
    }

    private void handlePlay(Player player, Map<String, Object> payload) {
        if (!"PLAYING".equals(stage)) return;
        if (!Objects.equals(player.getId(), currentTurnPlayerId)) return;

        List<Integer> cards = (List<Integer>) payload.get("cards");
        if (cards == null || cards.isEmpty()) return;

        // Simple validation: check if player has cards
        List<Integer> hand = playerHands.get(player.getId());
        if (hand == null) return;
        for (Integer c : cards) {
            if (!hand.contains(c)) return;
        }

        // Play cards
        for (Integer c : cards) hand.remove(Integer.valueOf(c));
        this.lastPlayedCards = new ArrayList<>(cards);
        this.lastPlayedBy = player.getId();

        if (hand.isEmpty()) {
            this.gameOver = true;
            this.stage = "RESULT";
        } else {
            // Turn rotation
            List<String> players = new ArrayList<>(playerHands.keySet());
            int idx = players.indexOf(currentTurnPlayerId);
            currentTurnPlayerId = players.get((idx + 1) % players.size());
        }

        broadcastState();
    }

    private void broadcastState() {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("landlordId", landlordId);
        state.put("currentTurn", currentTurnPlayerId);
        state.put("lastPlayed", lastPlayedCards);
        state.put("lastPlayer", lastPlayedBy);
        state.put("gameOver", gameOver);
        broadcaster.broadcast(state);

        // Private hands
        for (Map.Entry<String, List<Integer>> entry : playerHands.entrySet()) {
            sendPrivateHand(entry.getKey(), entry.getValue());
        }
    }

    private void sendPrivateHand(String pid, List<Integer> hand) {
        Player p = room.findById(pid).orElse(null);
        if (p == null || p.getIp() == null) return;
        for (String sid : sessions.getSessionsByIp(p.getIp())) {
            org.springframework.messaging.simp.SimpMessageHeaderAccessor h = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
            h.setSessionId(sid);
            h.setLeaveMutable(true);
            messaging.convertAndSendToUser(sid, "/queue/game.fightlandlord.private", hand, h.getMessageHeaders());
        }
    }

    @Override
    public void stop() { this.running = false; }
}
