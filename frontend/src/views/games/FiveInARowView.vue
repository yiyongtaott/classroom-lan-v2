<template>
  <GameBaseView title="🏮 五子棋" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="turn-info">
          <el-tag v-if="turn" :type="isMyTurn ? 'success' : 'info'" size="large" effect="dark">
            {{ isMyTurn ? '你的回合 ⚪' : '对方回合 ⚫' }}
          </el-tag>
        </div>
        <div class="game-msg">
          <el-alert v-if="winner" :type="winner === myId ? 'success' : 'error'" :closable="false" center>
            {{ winner === myId ? '🎉 你赢了！' : '❌ 你输了' }}
          </el-alert>
          <el-alert v-else-if="isDraw" type="warning" :closable="false" center>平局！</el-alert>
        </div>
      </div>

      <div class="board-wrapper">
        <div class="board">
          <div v-for="i in 15" :key="i" class="row">
            <div v-for="j in 15" :key="j" class="cell" @click="placePiece(i-1, j-1)">
              <div v-if="board[i-1][j-1] === 1" class="piece black"></div>
              <div v-if="board[i-1][j-1] === 2" class="piece white"></div>
            </div>
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
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const board = ref(Array(15).fill().map(() => Array(15).fill(0)))
const turn = ref(null)
const winner = ref(null)
const isDraw = ref(false)

const myId = computed(() => roomStore.self?.id)
const isMyTurn = computed(() => turn.value === myId.value)

function placePiece(x, y) {
  if (!isMyTurn.value || winner.value || isDraw.value) return
  stomp.publish(APP.GAME_ACTION, { x, y })
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'FIVE_IN_A_ROW') return
  const data = state.data || {}

  if (data.stage === 'START') {
    board.value = Array(15).fill().map(() => Array(15).fill(0))
    turn.value = data.turn
    winner.value = null
    isDraw.value = false
  } else if (data.stage === 'MOVE') {
    if (data.x !== undefined && data.y !== undefined) {
      board.value[data.x][data.y] = data.player
    }
    turn.value = data.turn
    winner.value = data.winner
    isDraw.value = data.draw
  } else if (data.stage === 'STOP') {
    winner.value = 'STOPPED'
  }
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1.5rem; }
.status-bar {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
  width: 100%;
}
.turn-info { height: 40px; display: flex; align-items: center; }
.game-msg { width: 100%; max-width: 400px; }

.board-wrapper {
  padding: 20px;
  background: #fef3c7;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}
.board {
  background: #d9b38c;
  padding: 10px;
  border: 2px solid #8b4513;
  border-radius: 4px;
}
.row { display: flex; }
.cell {
  width: 30px; height: 30px;
  border: 1px solid #8b4513;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  position: relative;
  transition: background 0.2s;
}
.cell:hover { background: rgba(255,255,255,0.3); }

.piece {
  width: 24px; height: 24px;
  border-radius: 50%;
  transition: transform 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.piece.black {
  background: radial-gradient(circle at 30% 30%, #444, #000);
  box-shadow: 2px 2px 4px rgba(0,0,0,0.4);
}
.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ccc);
  box-shadow: 2px 2px 4px rgba(0,0,0,0.2);
}
</style>
