<template>
  <GameBaseView title="🐍 贪吃蛇" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="score-board">
          <span class="label">得分:</span>
          <span class="value">{{ score }}</span>
        </div>
        <div v-if="gameOver" class="game-over-tag">游戏结束</div>
      </div>

      <div class="board-wrapper">
        <div class="board">
          <div v-for="r in 20" :key="r" class="row">
            <div v-for="c in 20" :key="c" class="cell" :class="getCellClass(r-1, c-1)"></div>
          </div>
        </div>
      </div>

      <div class="controls-guide">
        <p>使用方向键 <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> 控制方向</p>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="游戏结束" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.1rem; margin-bottom: 1rem;">
          最终得分: <strong style="color: #409eff; font-size: 1.5rem;">{{ score }}</strong>
        </p>
        <template #footer>
          <el-button type="primary" @click="restart">重新开始</el-button>
        </template>
      </div>
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
const snake = ref([])
const food = ref(null)
const score = ref(0)
const gameOver = ref(false)

function handleKeyDown(e) {
  if (gameOver.value) return
  let action = ''
  switch (e.code) {
    case 'ArrowUp': action = 'UP'; break;
    case 'ArrowDown': action = 'DOWN'; break;
    case 'ArrowLeft': action = 'LEFT'; break;
    case 'ArrowRight': action = 'RIGHT'; break;
  }
  if (action) {
    e.preventDefault()
    stomp.publish(APP.GAME_ACTION, { action })
  }
}

function stop() { stomp.publish(APP.GAME_STOP, {}) }
function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

onMounted(() => window.addEventListener('keydown', handleKeyDown))
onUnmounted(() => window.removeEventListener('keydown', handleKeyDown))

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'SNAKE') return
  const data = state.data || state

  snake.value = data.snake || []
  food.value = data.food || null
  score.value = data.score || 0
  gameOver.value = data.gameOver || false
})

function getCellClass(r, c) {
  if (food.value && food.value[0] === r && food.value[1] === c) return 'food'
  if (snake.value.some(p => p[0] === r && p[1] === c)) {
    const isHead = snake.value[0][0] === r && snake.value[0][1] === c
    return isHead ? 'snake-head' : 'snake-body'
  }
  return ''
}
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1.5rem; }
.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 420px;
}
.score-board {
  font-size: 1.2rem;
  font-weight: bold;
  background: #f1f5f9;
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  border: 1px solid #e2e8f0;
}
.score-board .label { color: #64748b; margin-right: 0.5rem; }
.score-board .value { color: #16a34a; }

.game-over-tag {
  background: #fee2e2;
  color: #ef4444;
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  font-weight: bold;
  border: 1px solid #fecaca;
}

.board-wrapper {
  padding: 15px;
  background: #1e293b;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.3);
}
.board {
  background: #0f172a;
  border: 2px solid #334155;
}
.row { display: flex; }
.cell {
  width: 20px; height: 20px;
  border: 1px solid #1e293b;
  box-sizing: border-box;
}

.cell.food {
  background: #ef4444;
  border-radius: 50%;
  box-shadow: 0 0 8px #ef4444;
}
.cell.snake-head {
  background: #22c55e;
  border-radius: 4px;
  box-shadow: 0 0 5px #22c55e;
}
.cell.snake-body {
  background: #16a34a;
  border-radius: 2px;
}

.controls-guide {
  text-align: center;
  color: #64748b;
  font-size: 0.9rem;
}
.controls-guide kbd {
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  padding: 2px 6px;
  font-family: monospace;
  font-weight: bold;
}

.game-over-content { text-align: center; }
</style>
