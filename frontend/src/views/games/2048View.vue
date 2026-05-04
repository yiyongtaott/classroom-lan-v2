<template>
  <GameBaseView title="2️⃣0️⃣4️⃣8️⃣" @stop="stop">
    <div class="game-container">
      <div class="score-box">得分: {{ score }}</div>
      <div class="board">
        <div v-for="r in 4" :key="r" class="row">
          <div v-for="c in 4" :key="c" class="cell" :class="getCellClass(r-1, c-1)">
            {{ board[r-1][c-1] !== 0 ? board[r-1][c-1] : '' }}
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="游戏结束" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.2rem; margin-bottom: 1rem;">
          最终得分: <strong style="color: #409eff;">{{ score }}</strong>
        </p>
      </div>
      <template #footer>
        <el-button type="primary" @click="restart">重新开始</el-button>
      </template>
    </el-dialog>
  </GameBaseView>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const board = ref(Array(4).fill().map(() => Array(4).fill(0)))
const score = ref(0)
const gameOver = ref(false)

function handleKeyDown(e) {
  if (gameOver.value) return

  let action = ''
  switch (e.code) {
    case 'ArrowUp': action = 'UP'; break
    case 'ArrowDown': action = 'DOWN'; break
    case 'ArrowLeft': action = 'LEFT'; break
    case 'ArrowRight': action = 'RIGHT'; break
  }

  if (action) {
    e.preventDefault()
    stomp.publish(APP.GAME_ACTION, { action })
  }
}

function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

onMounted(() => window.addEventListener('keydown', handleKeyDown))
onUnmounted(() => window.removeEventListener('keydown', handleKeyDown))

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'GAME_2048') return
  const data = state.data || state // Handle both nested and flat state

  board.value = data.board || Array(4).fill().map(() => Array(4).fill(0))
  score.value = data.score || 0
  gameOver.value = data.gameOver || false
})

function getCellClass(r, c) {
  const val = board.value[r][c]
  return val ? `cell-${val}` : ''
}
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1rem; }
.score-box { font-size: 1.5rem; font-weight: bold; color: #409eff; }
.board { background: #bbada0; padding: 10px; border-radius: 8px; display: grid; gap: 10px; }
.row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.cell {
  width: 70px; height: 70px; background: #cdc1b4; border-radius: 4px;
  display: flex; justify-content: center; align-items: center;
  font-weight: bold; font-size: 24px; color: #776e65;
  transition: transform 0.1s, background-color 0.2s;
}
.cell-2 { background: #eee4da; color: #776e65; }
.cell-4 { background: #ede0c8; color: #776e65; }
.cell-8 { background: #f2b179; color: white; }
.cell-16 { background: #f59563; color: white; }
.cell-32 { background: #f67c5f; color: white; }
.cell-64 { background: #f65e3b; color: white; }
.cell-128 { background: #edcf72; color: white; font-size: 20px; }
.cell-256 { background: #edcc61; color: white; font-size: 20px; }
.cell-512 { background: #edcc61; color: white; font-size: 20px; }
.cell-1024 { background: #edcc61; color: white; font-size: 16px; }
.cell-2048 { background: #edcc61; color: white; font-size: 16px; }

.game-over-content { text-align: center; }
</style>
