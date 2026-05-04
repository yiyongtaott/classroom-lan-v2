<template>
  <GameBaseView title="🛡️ 坦克地主" @stop="stop">
    <div class="game-container">
      <div class="status-bar">
        <div class="game-info">
          <span v-if="gameOver" class="game-over-tag">游戏结束!</span>
          <span v-else class="status-text">战斗中... 💥</span>
        </div>
      </div>

      <div class="board-wrapper">
        <div class="tank-board">
          <!-- Walls -->
          <div
            v-for="(w, i) in walls"
            :key="'wall-'+i"
            class="wall"
            :style="{ left: w.c * 30 + 'px', top: w.r * 30 + 'px' }"
          ></div>

          <!-- Bullets -->
          <div
            v-for="(b, i) in bullets"
            :key="'bullet-'+i"
            class="bullet"
            :style="{ left: b.c * 30 + 12 + 'px', top: b.r * 30 + 12 + 'px' }"
          ></div>

          <!-- Tanks -->
          <div
            v-for="(t, id) in tanks"
            :key="id"
            class="tank"
            :class="t.ownerId === myId ? 'my-tank' : 'enemy-tank'"
            :style="{ left: t.c * 30 + 'px', top: t.r * 30 + 'px', transform: `rotate(${t.dir * 90}deg)` }"
          >
            <div class="tank-body"></div>
            <div class="tank-turret"></div>
            <div class="health-bar">
              <div class="health-fill" :style="{ width: t.health + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="controls-guide">
        <p>使用 <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> 移动, 按 <kbd>Space</kbd> 发射</p>
      </div>
    </div>

    <el-dialog v-model="gameOver" title="战斗结果" width="300" center>
      <div class="game-over-content">
        <p style="text-align: center; font-size: 1.1rem; margin-bottom: 1rem;">
          战斗已结束! 🏁
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
const tanks = ref({})
const bullets = ref([])
const walls = ref([])
const gameOver = ref(false)

const myId = computed(() => roomStore.self?.id)

function handleKeyDown(e) {
  if (gameOver.value) return
  let action = ''
  switch (e.code) {
    case 'ArrowUp': action = 'UP'; break;
    case 'ArrowDown': action = 'DOWN'; break;
    case 'ArrowLeft': action = 'LEFT'; break;
    case 'ArrowRight': action = 'RIGHT'; break;
    case 'Space': action = 'FIRE'; break;
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
  if (!state || state.gameType !== 'TANK_LANDLORD') return
  const data = state.data || state

  tanks.value = data.tanks || {}
  bullets.value = data.bullets || []
  walls.value = data.walls || []
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
.tank-board {
  position: relative;
  width: 600px;
  height: 450px;
  background: #1e293b;
  border: 4px solid #475569;
}

.wall {
  position: absolute;
  width: 30px;
  height: 30px;
  background: #64748b;
  border: 2px solid #475569;
  box-sizing: border-box;
}

.bullet {
  position: absolute;
  width: 6px;
  height: 6px;
  background: #fbbf24;
  border-radius: 50%;
  box-shadow: 0 0 5px #fbbf24;
}

.tank {
  position: absolute;
  width: 30px;
  height: 30px;
  transition: all 0.1s ease-out;
}
.tank-body {
  width: 100%; height: 100%;
  background: #475569;
  border: 2px solid #1e293b;
  border-radius: 4px;
}
.my-tank .tank-body { background: #3b82f6; border-color: #1d4ed8; }
.enemy-tank .tank-body { background: #ef4444; border-color: #b91c1c; }

.tank-turret {
  position: absolute;
  top: 10px; left: 12px;
  width: 6px; height: 15px;
  background: inherit;
  border: 1px solid #1e293b;
}
.my-tank .tank-turret { background: #3b82f6; }
.enemy-tank .tank-turret { background: #ef4444; }

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
