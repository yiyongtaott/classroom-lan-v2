<template>
  <GameBaseView title="🧱 打砖块" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="score-board">
          <span class="label">得分:</span>
          <span class="value">{{ score }}</span>
        </div>
        <div v-if="gameOver" class="game-over-tag">游戏结束</div>
      </div>

      <div class="board-wrapper">
        <div class="game-board" ref="boardEl">
          <!-- Bricks -->
          <div
            v-for="(b, i) in bricks"
            :key="i"
            class="brick"
            :class="{ inactive: !b.active }"
            :style="{ left: b.c * 70 + 10 + 'px', top: b.r * 30 + 30 + 'px' }"
          ></div>

          <!-- Ball -->
          <div class="ball" :style="{ left: ball[0] + 'px', top: ball[1] + 'px' }"></div>

          <!-- Paddle -->
          <div class="paddle" :style="{ left: paddleX + 'px' }"></div>
        </div>
      </div>

      <div class="controls-guide">
        <p>使用方向键 <kbd>←</kbd> <kbd>→</kbd> 移动挡板</p>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="游戏结束" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.1rem; margin-bottom: 1rem;">
          最终得分: <strong style="color: #409eff; font-size: 1.5rem;">{{ score }}</strong>
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
const ball = ref([300, 300])
const paddleX = ref(250)
const bricks = ref([])
const score = ref(0)
const gameOver = ref(false)

function handleKeyDown(e) {
  if (gameOver.value) return
  let action = ''
  if (e.code === 'ArrowLeft') action = 'LEFT'
  else if (e.code === 'ArrowRight') action = 'RIGHT'
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
  if (!state || state.gameType !== 'BREAKOUT') return
  const data = state.data || state

  ball.value = data.ball || [300, 300]
  paddleX.value = data.paddleX || 250
  bricks.value = data.bricks || []
  score.value = data.score || 0
  gameOver.value = data.gameOver || false
})
</script>

<style scoped>
.game-container { display: flex; flex-direction: column; align-items: center; gap: 1.5rem; }
.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 600px;
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
.game-board {
  position: relative;
  width: 600px;
  height: 400px;
  background: #0f172a;
  overflow: hidden;
  border: 4px solid #334155;
}

.brick {
  position: absolute;
  width: 60px;
  height: 20px;
  background: #f43f5e;
  border: 1px solid #9f1239;
  border-radius: 2px;
  transition: opacity 0.3s;
}
.brick.inactive { opacity: 0; pointer-events: none; }

.ball {
  position: absolute;
  width: 10px;
  height: 10px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 0 8px white;
  transition: all 0.1s linear;
}

.paddle {
  position: absolute;
  bottom: 0;
  width: 100px;
  height: 20px;
  background: #3b82f6;
  border-radius: 4px 4px 0 0;
  box-shadow: 0 -2px 10px rgba(59, 130, 246, 0.5);
  transition: left 0.1s ease-out;
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
