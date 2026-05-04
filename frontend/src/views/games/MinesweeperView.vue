<template>
  <GameBaseView title="💣 扫雷" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <el-tag v-if="msg" :type="msg.includes('胜利') ? 'success' : 'danger'" size="large">{{ msg }}</el-tag>
        <el-tag v-else type="info" size="large">💣 {{ mineCount }}</el-tag>
      </div>

      <div class="board">
        <div v-for="r in rows" :key="r" class="row">
          <div
            v-for="c in cols"
            :key="c"
            class="cell"
            :class="{ revealed: revealed[r-1][c-1], mine: board && board[r-1][c-1] === 9, flagged: flagged[r-1][c-1] }"
            @click="revealCell(r-1, c-1)"
            @contextmenu.prevent="flagCell(r-1, c-1)"
          >
            <template v-if="revealed[r-1][c-1]">
              <span v-if="board && board[r-1][c-1] === 9">💣</span>
              <span v-else-if="board && board[r-1][c-1] > 0" :class="'num-' + board[r-1][c-1]">
                {{ board[r-1][c-1] }}
              </span>
            </template>
            <template v-else-if="flagged[r-1][c-1]">
              <span class="flag">🚩</span>
            </template>
            <template v-else>
              <span class="placeholder">?</span>
            </template>
          </div>
        </div>
      </div>
      <div class="controls">
        <p class="hint">左键：揭开 | 右键：标记🚩</p>
      </div>
    </div>
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

const rows = ref(10)
const cols = ref(10)
const mineCount = ref(0)
const board = ref(null)
const revealed = ref(Array(10).fill().map(() => Array(10).fill(false)))
const flagged = ref(Array(10).fill().map(() => Array(10).fill(false)))
const msg = ref('')

function revealCell(x, y) {
  stomp.publish(APP.GAME_ACTION, { action: 'REVEAL', x, y })
}

function flagCell(x, y) {
  stomp.publish(APP.GAME_ACTION, { action: 'FLAG', x, y })
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'MINESWEEPER') return
  const data = state.data || {}

  if (data.stage === 'START') {
    rows.value = data.rows || 10
    cols.value = data.cols || 10
    mineCount.value = data.mineCount || 10
    revealed.value = Array(rows.value).fill().map(() => Array(cols.value).fill(false))
    flagged.value = Array(rows.value).fill().map(() => Array(cols.value).fill(false))
    board.value = null
    msg.value = ''
  } else if (data.stage === 'MOVE') {
    if (data.revealed) revealed.value = data.revealed
    if (data.flagged) flagged.value = data.flagged
    if (data.gameOver) {
      board.value = data.board
      msg.value = data.msg
    } else if (data.msg) {
      msg.value = data.msg
    }
  } else if (data.stage === 'STOP') {
    msg.value = data.msg
  }
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1rem; }
.status-bar { font-weight: 600; }

.board { background: #cbd5e1; padding: 10px; border-radius: 4px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.row { display: flex; }
.cell {
  width: 30px; height: 30px; border: 1px solid #94a3b8;
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  background: #f1f5f9; font-weight: 700; font-size: 0.8rem;
  transition: all 0.1s;
  user-select: none;
}
.cell:hover:not(.revealed) { background: #e2e8f0; }
.cell.revealed { background: #fff; cursor: default; }
.cell.mine { background: #fecaca; }
.cell.flagged { background: #fee2e2; }
.placeholder { color: #cbd5e1; }
.flag { font-size: 0.8rem; }

.num-1 { color: #3b82f6; }
.num-2 { color: #10b981; }
.num-3 { color: #ef4444; }
.num-4 { color: #8b5f6 { color: #8b5cf6; }
.num-5 { color: #f59e0b; }
.num-6 { color: #06b6d4; }
.num-7 { color: #4b5563; }
.num-8 { color: #1e293b; }

.controls { text-align: center; color: #64748b; font-size: 0.85rem; }
</style>
