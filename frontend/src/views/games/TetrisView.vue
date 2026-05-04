<template>
  <GameBaseView title="🧱 俄罗斯方块" @stop="stop">
    <div class="layout">
      <div class="game-area">
        <div class="score-display">得分: {{ score }}</div>
        <div class="board">
          <div v-for="r in 20" :key="r" class="row">
            <div v-for="c in 10" :key="c" class="cell" :class="getCellColor(r-1, c-1)"></div>
          </div>
        </div>
      </div>

      <div class="side-panel">
        <div class="controls-guide">
          <h4>操作指南</h4>
          <ul>
            <li><kbd>←</kbd> 左移</li>
            <li><kbd>→</kbd> 右移</li>
            <li><kbd>↑</kbd> 旋转</li>
            <li><kbd>↓</kbd> 下落</li>
            <li><kbd>Space</kbd> 硬掉落</li>
          </ul>
        </div>
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
const board = ref(Array(20).fill().map(() => Array(10).fill(0)))
const currentPiece = ref(null)
const score = ref(0)
const gameOver = ref(false)

function handleKeyDown(e) {
  if (gameOver.value) return

  let action = ''
  switch (e.code) {
    case 'ArrowLeft': action = 'LEFT'; break
    case 'ArrowRight': action = 'RIGHT'; break
    case 'ArrowUp': action = 'ROTATE'; break
    case 'ArrowDown': action = 'DOWN'; break
    case 'Space': action = 'HARD_DROP'; break
  }

  if (action) {
    e.preventDefault()
    stomp.publish(APP.GAME_ACTION, { action })
  }
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

onMounted(() => window.addEventListener('keydown', handleKeyDown))
onUnmounted(() => window.removeEventListener('keydown', handleKeyDown))

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'TETRIS') return
  const data = state.data || state // Handle both nested and flat state

  if (data.stage === 'START') {
    board.value = data.board || Array(20).fill().map(() => Array(10).fill(0))
    score.value = 0
    gameOver.value = false
  } else if (data.stage === 'MOVE' || data.stage === 'TICK') {
    board.value = data.board || board.value
    currentPiece.value = data.piece
    score.value = data.score || 0
    gameOver.value = data.gameOver || false
  } else if (data.stage === 'STOP') {
    gameOver.value = true
  }
})

function getCellColor(r, c) {
  if (board.value && board.value[r] && board.value[r][c] !== 0) {
    return `color-type-${board.value[r][c]}`
  }
  if (currentPiece.value) {
    const { r: pr, c: pc, shape } = currentPiece.value
    const lr = r - pr
    const lc = c - pc
    if (lr >= 0 && lr < shape.length && lc >= 0 && lc < shape[0].length) {
      if (shape[lr][lc] !== 0) {
        return `color-type-${currentPiece.value.type}`
      }
    }
  }
  return ''
}
</script>

<style scoped>
.layout { display: flex; gap: 2rem; align-items: flex-start; justify-content: center; }

.game-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.score-display {
  font-size: 1.5rem;
  font-weight: bold;
  color: #374151;
  background: #f1f5f9;
  padding: 0.5rem 1.5rem;
  border-radius: 9999px;
  border: 1px solid #e2e8f0;
}

.board {
  background: #111827;
  padding: 6px;
  border-radius: 8px;
  box-shadow: 0 20px 25px -5px rgba(0,0,0,0.3);
  border: 4px solid #374151;
}
.row { display: flex; }
.cell {
  width: 28px; height: 28px;
  border: 1px solid #1f2937;
  box-sizing: border-box;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.controls-guide {
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  width: 220px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);
}
.controls-guide h4 { margin: 0 0 1rem 0; color: #374151; font-size: 1rem; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.5rem; }
.controls-guide ul { list-style: none; padding: 0; margin: 0; font-size: .9rem; color: #6b7280; }
.controls-guide li { margin-bottom: .75rem; display: flex; align-items: center; gap: .5rem; }
.controls-guide kbd {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  padding: 2px 6px;
  font-family: monospace;
  font-weight: bold;
  color: #374151;
}

.game-over-content { text-align: center; }

/* Block Colors with Glossy Effect */
.color-type-1 { background: #00ffff; box-shadow: inset 0 0 8px #00cccc, 0 0 4px #00ffff; } /* I */
.color-type-2 { background: #0000ff; box-shadow: inset 0 0 8px #0000cc, 0 0 4px #0000ff; } /* J */
.color-type-3 { background: #ff7f00; box-shadow: inset 0 0 8px #cc6600, 0 0 4px #ff7f00; } /* L */
.color-type-4 { background: #ffff00; box-shadow: inset 0 0 8px #cccc00, 0 0 4px #ffff00; } /* O */
.color-type-5 { background: #00ff00; box-shadow: inset 0 0 8px #00cc00, 0 0 4px #00ff00; } /* S */
.color-type-6 { background: #800080; box-shadow: inset 0 0 8px #660066, 0 0 4px #800080; } /* T */
.color-type-7 { background: #ff0000; box-shadow: inset 0 0 8px #cc0000, 0 0 4px #ff0000; } /* Z */
</style>
