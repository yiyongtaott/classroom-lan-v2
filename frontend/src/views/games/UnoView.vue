<template>
  <GameBaseView title="UNO️⃣ UNO 纸牌" @stop="stop">
    <div class="game-container">
      <div class="game-header">
        <div class="status-bar">
          <span v-if="gameOver" class="winner-text">游戏结束!</span>
          <span v-else class="turn-text">
            {{ isMyTurn ? '轮到你了! 🃏' : '等待玩家: ' + currentTurnPlayerName }}
          </span>
        </div>
        <div class="top-card-area">
          <div class="top-card-label">当前顶牌</div>
          <div class="top-card" :class="topCardColor">
            {{ topCard }}
          </div>
        </div>
      </div>

      <div class="player-area">
        <div class="my-hand">
          <div class="hand-label">我的手牌 ({{ hand.length }}张)</div>
          <div class="cards-grid">
            <button
              v-for="(card, i) in hand"
              :key="i"
              class="card-btn"
              :class="card.split(' ')[0].toLowerCase()"
              @click="playCard(card)"
              :disabled="!isMyTurn"
            >
              {{ card }}
            </button>
          </div>
        </div>
        <div class="action-area">
          <button class="btn-draw" @click="drawCard" :disabled="!isMyTurn">
            摸一张牌 🃏
          </button>
        </div>
      </div>
    </div>
  </GameBaseView>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { useUserListStore } from '../../stores/userList'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const userList = useUserListStore()

const topCard = ref('')
const hand = ref([])
const turnIdx = ref(0)
const turnOrder = ref([])
const gameOver = ref(false)

const isMyTurn = computed(() => {
  return roomStore.self?.id === turnOrder.value[turnIdx.value]
})

const currentTurnPlayerName = computed(() => {
  const pid = turnOrder.value[turnIdx.value]
  return userList.users.find(u => u.id === pid)?.name || '未知玩家'
})

const topCardColor = computed(() => {
  if (!topCard.value) return ''
  return topCard.value.split(' ')[0].toLowerCase()
})

function playCard(card) {
  stomp.publish(APP.GAME_ACTION, { action: 'PLAY', card })
}

function drawCard() {
  stomp.publish(APP.GAME_ACTION, { action: 'DRAW' })
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

// Listen for general game state
watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'UNO') return
  topCard.value = state.topCard
  turnIdx.value = state.turnIdx || 0
  turnOrder.value = state.turnOrder || []
  gameOver.value = state.gameOver || false
})

// Listen for private hand updates
watch(() => roomStore.unoHand, (newHand) => {
  if (newHand) {
    hand.value = newHand
  }
})
</script>

<style scoped>
.game-container {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding: 1rem;
  max-width: 800px;
  margin: 0 auto;
}

.game-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  background: #f8fafc;
  padding: 2rem;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
}

.status-bar {
  font-size: 1.2rem;
  font-weight: bold;
  color: #64748b;
}

.winner-text {
  color: #ef4444;
  font-size: 1.5rem;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.05); }
  100% { transform: scale(1); }
}

.top-card-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.top-card-label {
  font-size: 0.9rem;
  color: #94a3b8;
}

.top-card {
  width: 100px;
  height: 140px;
  border-radius: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 1.5rem;
  font-weight: bold;
  color: white;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
  box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1);
  transition: all 0.3s ease;
  border: 4px solid white;
}

.top-card.red { background: #ef4444; }
.top-card.blue { background: #3b82f6; }
.top-card.green { background: #22c55e; }
.top-card.yellow { background: #eab308; }
.top-card.wild { background: #1e293b; }

.player-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rem;
}

.my-hand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.hand-label {
  font-size: 1rem;
  color: #64748b;
  font-weight: 500;
}

.cards-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}

.card-btn {
  width: 70px;
  height: 100px;
  border-radius: 8px;
  border: 2px solid white;
  color: white;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);
  font-size: 0.9rem;
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 5px;
}

.card-btn:hover:not(:disabled) {
  transform: translateY(-10px);
  box-shadow: 0 12px 20px -5px rgba(0,0,0,0.2);
}

.card-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  filter: grayscale(0.5);
}

.card-btn.red { background: #ef4444; }
.card-btn.blue { background: #3b82f6; }
.card-btn.green { background: #22c55e; }
.card-btn.yellow { background: #eab308; }
.card-btn.wild { background: #1e293b; }

.action-area {
  display: flex;
  justify-content: center;
}

.btn-draw {
  padding: 0.75rem 1.5rem;
  background: #f1f5f9;
  border: 2px solid #cbd5e1;
  border-radius: 9999px;
  color: #475569;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-draw:hover:not(:disabled) {
  background: #e2e8f0;
  border-color: #94a3b8;
  color: #1e293b;
}

.btn-draw:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
