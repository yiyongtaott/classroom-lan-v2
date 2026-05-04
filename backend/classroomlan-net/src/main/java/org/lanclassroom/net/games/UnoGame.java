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
public class UnoGame implements GameSession {
    private Room room;
    private Broadcaster broadcaster;
    private final SimpMessagingTemplate messaging;
    private final ClientSessionRegistry sessions;
    private volatile boolean running;
    private boolean gameOver = false;

    private final List<Card> deck = new CopyOnWriteArrayList<>();
    private final List<Card> discardPile = new CopyOnWriteArrayList<>();
    private final Map<String, List<Card>> playerHands = new ConcurrentHashMap<>();
    private final List<String> turnOrder = new CopyOnWriteArrayList<>();
    private int currentTurnIdx = 0;
    private int direction = 1;
    private Card topCard;

    public static class Card {
        public final String color;
        public final String value;
        public Card(String color, String value) { this.color = color; this.value = value; }
        @Override public String toString() { return color + " " + value; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Card card = (Card) o;
            return Objects.equals(color, card.color) && Objects.equals(value, card.value);
        }
        @Override public int hashCode() { return Objects.hash(color, value); }
    }

    public UnoGame(SimpMessagingTemplate messaging, ClientSessionRegistry sessions) {
        this.messaging = messaging;
        this.sessions = sessions;
    }

    @Override
    public GameType getType() { return GameType.UNO; }

    @Override
    public void start(Room room, Broadcaster broadcaster) {
        this.room = room;
        this.broadcaster = broadcaster;
        this.running = true;
        this.gameOver = false;
        this.playerHands.clear();
        this.turnOrder.clear();
        this.discardPile.clear();

        for (Player p : room.getPlayers()) {
            if (Player.STATUS_ONLINE.equals(p.getStatus())) {
                turnOrder.add(p.getId());
            }
        }

        if (turnOrder.isEmpty()) {
            this.running = false;
            return;
        }

        initializeDeck();
        shuffleDeck();
        dealCards();

        topCard = drawCard();
        discardPile.add(topCard);
        currentTurnIdx = 0;
        direction = 1;
        broadcastState("START");
    }

    private void initializeDeck() {
        deck.clear();
        String[] colors = {"Red", "Blue", "Green", "Yellow"};
        String[] values = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Reverse", "Draw2"};
        for (String c : colors) {
            for (String v : values) {
                deck.add(new Card(c, v));
                if (!v.equals("0")) deck.add(new Card(c, v));
            }
        }
        for (int i = 0; i < 4; i++) {
            deck.add(new Card("Wild", "Wild"));
            deck.add(new Card("Wild", "WildDraw4"));
        }
    }

    private void shuffleDeck() { Collections.shuffle(deck); }

    private void dealCards() {
        for (String pid : turnOrder) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < 7; i++) hand.add(drawCard());
            playerHands.put(pid, hand);
        }
    }

    private Card drawCard() {
        if (deck.isEmpty()) {
            if (discardPile.size() <= 1) return new Card("Wild", "Wild");
            List<Card> temp = new ArrayList<>(discardPile);
            Card currentTop = temp.remove(temp.size() - 1);
            deck.clear();
            deck.addAll(temp);
            Collections.shuffle(deck);
            discardPile.clear();
            discardPile.add(currentTop);
        }
        return deck.remove(0);
    }

    @Override
    public void handleAction(Player player, Map<String, Object> payload) {
        if (!running || gameOver) return;
        if (!Objects.equals(player.getId(), turnOrder.get(currentTurnIdx))) return;

        String action = (String) payload.get("action");
        if ("PLAY".equals(action)) {
            handlePlayCard(player, payload);
        } else if ("DRAW".equals(action)) {
            handleDrawCard(player);
        }
    }

    private void handlePlayCard(Player player, Map<String, Object> payload) {
        String cardStr = (String) payload.get("card");
        if (cardStr == null) return;
        String[] parts = cardStr.split(" ");
        if (parts.length < 2) return;
        Card card = new Card(parts[0], parts[1]);

        List<Card> hand = playerHands.get(player.getId());
        if (hand == null || !hand.contains(card)) return;
        if (!canPlay(card)) return;

        hand.remove(card);
        topCard = card;
        discardPile.add(card);

        if (hand.isEmpty()) {
            gameOver = true;
            broadcastState("WINNER");
            return;
        }

        processSpecialCard(card);
        broadcastState("UPDATE");
    }

    private boolean canPlay(Card card) {
        if ("Wild".equals(card.color)) return true;
        if (Objects.equals(card.color, topCard.color)) return true;
        if (Objects.equals(card.value, topCard.value)) return true;
        return false;
    }

    private void processSpecialCard(Card card) {
        if ("Skip".equals(card.value)) {
            nextTurn();
        } else if ("Reverse".equals(card.value)) {
            direction *= -1;
            if (turnOrder.size() == 2) nextTurn();
        } else if ("Draw2".equals(card.value)) {
            nextTurn();
            forceDraw(turnOrder.get(currentTurnIdx), 2);
        } else if ("WildDraw4".equals(card.value)) {
            nextTurn();
            forceDraw(turnOrder.get(currentTurnIdx), 4);
        } else {
            nextTurn();
        }
    }

    private void nextTurn() {
        currentTurnIdx = (currentTurnIdx + direction + turnOrder.size()) % turnOrder.size();
    }

    private void handleDrawCard(Player player) {
        List<Card> hand = playerHands.get(player.getId());
        if (hand != null) hand.add(drawCard());
        nextTurn();
        broadcastState("UPDATE");
    }

    private void forceDraw(String playerId, int count) {
        List<Card> hand = playerHands.get(playerId);
        if (hand != null) {
            for (int i = 0; i < count; i++) hand.add(drawCard());
        }
    }

    private void broadcastState(String stage) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", getType().name());
        state.put("stage", stage);
        state.put("topCard", topCard != null ? topCard.toString() : "None");
        state.put("gameOver", gameOver);
        state.put("turnIdx", currentTurnIdx);
        state.put("turnOrder", turnOrder);
        broadcaster.broadcast(state);

        for (String pid : turnOrder) {
            sendPrivateHand(pid);
        }
    }

    private void sendPrivateHand(String pid) {
        Player p = room.findById(pid).orElse(null);
        if (p == null || p.getIp() == null) return;
        List<Card> hand = playerHands.getOrDefault(pid, Collections.emptyList());
        List<String> handStr = hand.stream().map(Card::toString).collect(Collectors.toList());

        for (String sid : sessions.getSessionsByIp(p.getIp())) {
            org.springframework.messaging.simp.SimpMessageHeaderAccessor h = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
            h.setSessionId(sid);
            h.setLeaveMutable(true);
            messaging.convertAndSendToUser(sid, "/queue/game.uno.private", handStr, h.getMessageHeaders());
        }
    }

    @Override
    public void stop() { this.running = false; }
}
