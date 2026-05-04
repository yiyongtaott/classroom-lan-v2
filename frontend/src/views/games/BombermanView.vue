<template>
  <GameBaseView title="💣 炸弹人" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="game-info">
          <span v-if="gameOver" class="game-over-tag">游戏结束!</span>
          <span v-else class="status-text">小心炸弹！💥</span>
        </div>
      </div>

      <div class="board-wrapper">
        <div class="game-board">
          <!-- Grid -->
          <div v-for="r in 15" :key="r" class="row">
            <div
              v-for="c in 15"
              :key="c"
              class="cell"
              :class="{ wall: grid[r-1][c-1] === 1, rock: grid[r-1][c-1] === 2 }"
            ></div>
          </div>

          <!-- Players -->
          <div
            v-for="(pos, id) in players"
            :key="id"
            class="player"
            :style="{ left: pos.c * 30 + 'px', top: pos.r * 30 + 'px' }"
          >
            <div class="player-icon">👤</div>
            <div class="health-bar">
              <div class="health-fill" :style="{ width: pos.health + '%' }"></div>
            </div>
          </div>

          <!-- Bombs -->
          <div
            v-for="(bomb, i) in bombs"
            :key="'bomb-'+i"
            class="bomb"
            :style="{ left: bomb.c * 30 + 'px', top: bomb.r * 30 + 'px' }"
          >
            💣
          </div>
        </div>
      </div>

      <div class="controls-guide">
        <p>使用方向键 <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> 移动, 按 <kbd>Space</kbd> 放置炸弹</p>
        <div class="mobile-controls">
          <div class="row-ctrl">
            <el-button @click="move(0, -1)">↑</el-button>
          </div>
          <div class="row-ctrl">
            <el-button @click="move(-1, 0)">←</el-button>
            <el-button type="danger" @click="placeBomb">💣</el-button>
            <el-button @click="move(1, 0)">→</el-button>
          </div>
          <div class="row-ctrl">
            <el-button @click="move(0, 1)">↓</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="游戏结束" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.1rem; margin-bottom: 1rem;">
          战斗结束！🏁
        </p>
        <template #footer>
          <el-button type="primary" @click="restart">重新挑战</el-button>
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
const players = ref({})
const bombs = ref([])
const grid = ref(Array(15).fill().map(() => Array(15).fill(0)))
const gameOver = ref(false)

function move(dr, dc) {
  stomp.publish(APP.GAME_ACTION, { action: dr === 0 ? (dc === 1 ? 'RIGHT' : 'LEFT') : (dr === -1 ? 'UP' : 'DOWN') })
}

function placeBomb() {
  stomp.publish(APP.GAME_ACTION, { action: 'PLACE_BOMB' })
}

function stop() { stomp.publish(APP.GAME_STOP, {}) }
function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

function handleKeyDown(e) {
  if (gameOver.value) return
  let action = ''
  switch (e.code) {
    case 'ArrowUp': action = 'UP'; break;
    case 'ArrowDown': action = 'DOWN'; break;
    case 'ArrowLeft': action = 'LEFT'; break;
    case 'ArrowRight': action = 'RIGHT'; break;
    case 'Space': action = 'PLACE_BOMB'; break;
  }
  if (action) {
    e.preventDefault()
    stomp.publish(APP.GAME_ACTION, { action })
  }
}

onMounted(() => window.addEventListener('keydown', handleKeyDown))
onUnmounted(() => window.removeEventListener('keydown', handleKeyDown))

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'BOMBERMAN') return
  const data = state.data || state

  players.value = data.players || {}
  bombs.value = data.bombs || []
  grid.value = data.grid || Array(15).fill().map(() => Array(15).fill(0))
  gameOver.value = data.gameOver || false
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1.5rem; }
.status-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
}
.game-over-tag {
  background: #fee2e2;
  color: #ef4444;
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  font-weight: bold;
  border: 1px solid #fecaca;
}
.status-text { font-weight: bold; color: #64748b; }

.board-wrapper {
  padding: 15px;
  background: #334155;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.3);
}
.game-board {
  position: relative;
  width: 450px;
  height: 450px;
  background: #1e293b;
  border: 4px solid #475569;
}
.row { display: flex; }
.cell {
  width: 30px; height: 30px;
  border: 1px solid #1e293b;
  box-sizing: border-box;
}
.cell.wall { background: #64748b; border: 2px solid #475569; }
.cell.rock { background: #94a3b8; border: 2px solid #cbd5e1; box-shadow: inset 0 0 5px #000; }

.player {
  position: absolute;
  width: 30px;
  height: 30px;
  transition: all 0.1s ease-out;
  z-index: 10;
}
.player-icon {
  width: 100%; height: 100%;
  display: flex; justify-content: center; align-items: center;
  font-size: 20px;
}
.health-bar {
  position: absolute;
  top: -12px; left: 0;
  width: 100%; height: 4px;
  background: #4b5563;
  border-radius: 2px;
}
.health-fill {
  height: 100%;
  background: #22c55e;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.bomb {
  position: absolute;
  width: 30px;
  height: 30px;
  display: flex; justify-content: center; align-items: center;
  font-size: 20px;
  z-index: 5;
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
.mobile-controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
}
.row-ctrl { display: flex; gap: 0.5rem; }

.game-over-content { text-align: center; }
</style>
