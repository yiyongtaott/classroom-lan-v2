<template>
  <GameBaseView title="🧩 数独" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="score-board">
          <span class="label">已填充:</span>
          <span class="value">{{ score }} / 81</span>
        </div>
        <div v-if="gameOver" class="game-over-tag">恭喜完成！🎉</div>
      </div>

      <div class="board-wrapper">
        <div class="sudoku-board">
          <div v-for="r in 9" :key="r" class="row">
            <div v-for="c in 9" :key="c" class="cell" @click="selectCell(r-1, c-1)">
              <input
                v-if="canEdit(r-1, c-1)"
                type="text"
                maxlength="1"
                v-model="cellValues[r-1][c-1]"
                @keyup.enter="submitValue(r-1, c-1)"
                @input="handleInput(r-1, c-1)"
                class="cell-input"
              />
              <span v-else class="cell-value">{{ board[r-1][c-1] || '' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="controls-guide">
        <p>点击格子输入数字，按下 <kbd>Enter</kbd> 提交</p>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="游戏结束" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.1rem; margin-bottom: 1rem;">
          你成功解决了这个数独！
        </p>
        <template #footer>
          <el-button type="primary" @click="restart">重新开始</el-button>
        </template>
      </div>
    </el-dialog>
  </GameBaseView>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const board = ref(Array(9).fill().map(() => Array(9).fill(0)))
const initial = ref(Array(9).fill().map(() => Array(9).fill(0)))
const cellValues = ref(Array(9).fill().map(() => Array(9).fill('')))
const score = ref(0)
const gameOver = ref(false)

function canEdit(r, c) {
  return initial.value[r][c] === 0
}

function handleInput(r, c) {
  const val = cellValues.value[r][c]
  if (!/^[1-9]$/.test(val)) {
    cellValues.value[r][c] = ''
  }
}

function submitValue(r, c) {
  const val = parseInt(cellValues.value[r][c])
  if (isNaN(val)) return
  stomp.publish(APP.GAME_ACTION, { r, c, val })
}

function stop() { stomp.publish(APP.GAME_STOP, {}) }
function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'SUDOKU') return
  const data = state.data || state

  board.value = data.board || Array(9).fill().map(() => Array(9).fill(0))
  initial.value = data.initial || Array(9).fill().map(() => Array(9).fill(0))
  score.value = data.score || 0
  gameOver.value = data.gameOver || false

  // Sync local input values with board
  for (let r = 0; r < 9; r++) {
    for (let c = 0; c < 9; c++) {
      cellValues.value[r][c] = board.value[r][c] ? board.value[r][c].toString() : ''
    }
  }
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1.5rem; }
.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 450px;
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
  background: #dcfce7;
  color: #16a34a;
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  font-weight: bold;
  border: 1px solid #bbf7d0;
}

.board-wrapper {
  padding: 15px;
  background: #f8fafc;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
  border: 2px solid #cbd5e1;
}
.sudoku-board {
  display: inline-block;
  border: 3px solid #334155;
}
.row { display: flex; }
.cell {
  width: 40px; height: 40px;
  border: 1px solid #cbd5e1;
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
/* Thicker borders for 3x3 grids */
.cell:nth-child(3n) { border-right: 3px solid #334155; }
.cell:nth-child(9) { border-right: 1px solid #cbd5e1; } /* Edge case */
.row:nth-child(3n) { border-bottom: 3px solid #334155; }

.cell-input {
  width: 100%; height: 100%;
  border: none; text-align: center;
  font-size: 1.2rem; font-weight: bold;
  outline: none; background: transparent;
  color: #3b82f6;
}
.cell-value {
  font-size: 1.2rem; font-weight: bold;
  color: #334155;
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
