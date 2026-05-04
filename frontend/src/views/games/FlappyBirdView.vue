<template>
  <GameBaseView title="🐦 像素鸟" @stop="stop">
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
          <!-- Bird -->
          <div class="bird" :style="{ top: birdY + 'px' }">🐦</div>

          <!-- Pipes -->
          <div
            v-for="(p, i) in pipes"
            :key="i"
            class="pipe-container"
            :style="{ left: p.x + 'px' }"
          >
            <div class="pipe top" :style="{ height: p.topHeight + 'px' }"></div>
            <div class="pipe bottom" :style="{ top: (p.topHeight + 150) + 'px' }"></div>
          </div>
        </div>
      </div>

      <div class="controls-guide">
        <p>点击屏幕或按下 <kbd>Space</kbd> / <kbd>↑</kbd> 跳跃</p>
        <button class="btn-flap" @click="flap" :disabled="gameOver">跳跃!</button>
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
const birdY = ref(200)
const pipes = ref([])
const score = ref(0)
const gameOver = ref(false)

function flap() { stomp.publish(APP.GAME_ACTION, { action: 'FLAP' }) }
function stop() { stomp.publish(APP.GAME_STOP, {}) }
function restart() {
  stomp.publish(APP.GAME_START, {})
  gameOver.value = false
}

function handleKeyDown(e) {
  if (gameOver.value) return
  if (e.code === 'Space' || e.code === 'ArrowUp') {
    e.preventDefault()
    flap()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeyDown))
onUnmounted(() => window.removeEventListener('keydown', handleKeyDown))

watch(() => roomStore.lastGameState, (state) => {
  if (!state || state.gameType !== 'FLAPPY_BIRD') return
  const data = state.data || state

  birdY.value = data.birdY || 200
  pipes.value = data.pipes || []
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
  background: #70c5ce;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
}
.game-board {
  position: relative;
  width: 600px;
  height: 400px;
  background: linear-gradient(to bottom, #70c5ce 0%, #98d8de 100%);
  overflow: hidden;
  border: 4px solid #fff;
}

.bird {
  position: absolute;
  left: 50px;
  width: 30px;
  height: 30px;
  font-size: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: top 0.1s linear;
  z-index: 10;
}

.pipe-container {
  position: absolute;
  width: 50px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.pipe {
  width: 50px;
  background: #73bf2e;
  border: 3px solid #558021;
  box-shadow: inset -4px 0 0 rgba(0,0,0,0.2);
}
.pipe.top { border-bottom-left-radius: 4px; border-bottom-right-radius: 4px; }
.pipe.bottom { position: absolute; bottom: 0; border-top-left-radius: 4px; border-top-right-radius: 4px; height: 100%; }
/* Fix bottom pipe height calculation in CSS since we use top: ... in JS */
.pipe.bottom {
  height: 100%;
  top: auto;
}
/* Need to overwrite the top property in JS */

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
.btn-flap {
  margin-top: 1rem;
  padding: 0.75rem 2rem;
  background: #16a34a;
  color: white;
  border: none;
  border-radius: 9999px;
  font-weight: bold;
  cursor: pointer;
  transition: transform 0.1s;
}
.btn-flap:active { transform: scale(0.95); }

.game-over-content { text-align: center; }
</style>
