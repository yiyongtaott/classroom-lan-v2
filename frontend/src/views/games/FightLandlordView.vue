<template>
  <GameBaseView title="🃏 斗地主" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="info-panel">
          <el-tag v-if="stage === 'BIDDING'" type="warning" size="large">抢地主阶段: 请决定是否抢地主</el-tag>
          <el-tag v-if="stage === 'PLAYING'" type="success" size="large">
            {{ currentTurn === myId ? '轮到你了！' : '等待玩家: ' + currentTurnPlayerName }}
          </el-tag>
          <el-tag v-if="stage === 'RESULT'" type="danger" size="large">游戏结束！</el-tag>
        </div>
      </div>

      <div class="table-area">
        <div class="center-pile">
          <div v-if="lastPlayed.length" class="last-cards">
            <div v-for="c in lastPlayed" :key="c" class="card-small">{{ formatCard(c) }}</div>
          </div>
          <div v-else class="empty-pile">等待出牌...</div>
        </div>

        <div class="player-hand">
          <div class="hand-label">我的手牌 ({{ hand.length }}张)</div>
          <div class="cards-grid">
            <div
              v-for="(c, i) in hand"
              :key="i"
              class="card-item"
              :class="{ selected: selectedCards.includes(i) }"
              @click="toggleCard(i)"
            >
              {{ formatCard(c) }}
            </div>
          </div>
          <div class="hand-actions">
            <el-button v-if="stage === 'BIDDING'" type="primary" @click="bid">抢地主</el-button>
            <el-button v-if="stage === 'BIDDING'" @click="pass">不抢</el-button>
            <el-button v-if="stage === 'PLAYING' && isMyTurn" type="success" @click="playCards" :disabled="selectedCards.length === 0">出牌</el-button>
          </div>
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

const stage = ref('WAITING')
const hand = ref([])
const lastPlayed = ref([])
const currentTurn = ref(null)
const selectedCards = ref([])
const landlordId = ref(null)

const myId = computed(() => roomStore.self?.id)
const isMyTurn = computed(() => currentTurn.value === myId.value)

const currentTurnPlayerName = computed(() => {
  return userList.users.find(u => u.id === currentTurn.value)?.name || '未知玩家'
})

function formatCard(val) {
  const map = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A' }
  return map[val] || val
}

function toggleCard(idx) {
  if (!isMyTurn.value) return
  const pos = selectedCards.value.indexOf(idx)
  if (pos > -1) selectedCards.value.splice(pos, 1)
  else selectedCards.value.push(idx)
}

function playCards() {
  const cardsToPlay = selectedCards.value.map(idx => hand.value[idx])
  stomp.publish(APP.GAME_ACTION, { action: 'PLAY', cards: cardsToPlay })
  selectedCards.value = []
}

function bid() { stomp.publish(APP.GAME_ACTION, { action: 'BID' }) }
function pass() { stomp.publish(APP.GAME_ACTION, { action: 'PASS' }) }
function stop() { stomp.publish(APP.GAME_STOP, {}) }

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'FIGHT_LANDLORD') return
  const data = state.data || state

  stage.value = data.stage || 'WAITING'
  currentTurn.value = data.currentTurn
  lastPlayed.value = data.lastPlayed || []
  landlordId.value = data.landlordId
})

watch(() => roomStore.fightLandlordHand, (newHand) => {
  if (newHand) hand.value = newHand
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 2rem; }
.status-bar { width: 100%; display: flex; justify-content: center; margin-bottom: 1rem; }
.info-panel { height: 40px; display: flex; align-items: center; }

.table-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3rem;
  width: 100%;
}

.center-pile {
  width: 400px;
  height: 120px;
  background: radial-gradient(circle, #1a472a 0%, #0a2e1a 100%);
  border: 8px solid #5d4037;
  border-radius: 60px;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #fff;
  box-shadow: inset 0 0 20px rgba(0,0,0,0.5);
}

.last-cards { display: flex; gap: 5px; }
.card-small {
  width: 30px; height: 45px;
  background: white; color: #333;
  border-radius: 4px;
  display: flex; justify-content: center; align-items: center;
  font-weight: bold; font-size: 0.8rem;
  border: 1px solid #ccc;
}

.empty-pile { color: #4caf50; font-style: italic; opacity: 0.7; }

.player-hand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}
.hand-label { font-weight: bold; color: #64748b; }
.cards-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.card-item {
  width: 40px; height: 60px;
  background: white;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  display: flex; justify-content: center; align-items: center;
  font-weight: bold; cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}
.card-item:hover { transform: translateY(-5px); border-color: #3b82f6; }
.card-item.selected {
  transform: translateY(-15px);
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 4px 6px rgba(59, 130, 246, 0.3);
}

.hand-actions { display: flex; gap: 1rem; margin-top: 1rem; }
</style>
