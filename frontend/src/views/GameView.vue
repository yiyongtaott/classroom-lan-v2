<template>
  <div class="game">
    <header>
      <h2>游戏大厅</h2>
      <span>当前游戏：{{ displayGameType }}</span>
    </header>

    <div class="game-area">
      <div v-if="!gameStarted" class="game-menu">
        <h3>选择游戏</h3>
        <div class="menu-grid">
          <button @click="selectGame('DRAW')">🎨 你画我猜</button>
          <button @click="selectGame('WEREWOLF')" disabled>🐺 狼人杀（开发中）</button>
          <button @click="selectGame('QUIZ')" disabled>📝 抢答（开发中）</button>
        </div>
      </div>

      <div v-else class="game-board">
        <div class="game-header">
          <button @click="endGame" class="btn-end">结束游戏</button>
        </div>
        <div v-if="currentGame === 'DRAW'" class="draw-game">
          <canvas ref="canvas" width="600" height="400" class="draw-canvas"></canvas>
          <div class="draw-tools">
            <input type="color" v-model="drawColor" />
            <input type="range" v-model="drawSize" min="1" max="20" />
            <button @click="clearCanvas">清空</button>
          </div>
          <p class="hint">提示词：{{ hintWord || '等待开始...' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoomStore } from '../stores/room'

const roomStore = useRoomStore()

const gameStarted = ref(false)
const currentGame = ref(null)
const hintWord = ref('苹果')
const drawColor = ref('#000000')
const drawSize = ref(3)
const canvas = ref(null)

const displayGameType = computed(() => {
  const map = { DRAW: '你画我猜', WEREWOLF: '狼人杀', QUIZ: '抢答' }
  return map[roomStore.gameType] || '未选择'
})

function selectGame(type) {
  currentGame.value = type
  gameStarted.value = true
  roomStore.setGameType(type)

  // TODO: 发送游戏启动消息到后端
  if (type === 'DRAW') {
    nextTick(initCanvas)
  }
}

function endGame() {
  gameStarted.value = false
  currentGame.value = null
  roomStore.setGameType(null)
  // TODO: 发送游戏结束消息
}

function initCanvas() {
  const ctx = canvas.value.getContext('2d')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.value.width, canvas.value.height)

  canvas.value.addEventListener('mousedown', startDraw)
  canvas.value.addEventListener('mousemove', draw)
  canvas.value.addEventListener('mouseup', stopDraw)
  canvas.value.addEventListener('mouseleave', stopDraw)
}

let drawing = false

function startDraw(e) {
  drawing = true
  const ctx = canvas.value.getContext('2d')
  ctx.beginPath()
  ctx.moveTo(e.offsetX, e.offsetY)
}

function draw(e) {
  if (!drawing) return
  const ctx = canvas.value.getContext('2d')
  ctx.strokeStyle = drawColor.value
  ctx.lineWidth = drawSize.value
  ctx.lineCap = 'round'
  ctx.lineTo(e.offsetX, e.offsetY)
  ctx.stroke()
}

function stopDraw() {
  drawing = false
}

function clearCanvas() {
  const ctx = canvas.value.getContext('2d')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.value.width, canvas.value.height)
}

onMounted(() => {
  // 游戏视图挂载时准备 STOMP 订阅（如游戏状态更新）
})
</script>

<style scoped>
.game {
  max-width: 800px;
  margin: 2rem auto;
  padding: 1rem;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}
.game-menu .menu-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}
.game-menu button {
  padding: 2rem;
  font-size: 1.2rem;
  background: #fff;
  border: 2px solid #42b983;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.game-menu button:disabled {
  border-color: #ccc;
  color: #ccc;
  cursor: not-allowed;
}
.game-menu button:hover:not(:disabled) {
  background: #42b983;
  color: white;
}
.game-board {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 1rem;
}
.game-header {
  margin-bottom: 1rem;
}
.btn-end {
  padding: 0.5rem 1rem;
  background: #f56c6c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.draw-canvas {
  border: 1px solid #ddd;
  background: white;
  display: block;
  margin: 1rem auto;
}
.draw-tools {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: center;
  margin: 1rem 0;
}
.hint {
  text-align: center;
  font-size: 1.2rem;
  color: #666;
}
</style>
