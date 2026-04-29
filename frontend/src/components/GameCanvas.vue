<template>
  <div class="game-canvas-wrapper">
    <canvas ref="canvasRef" class="draw-canvas" width="600" height="400"></canvas>
    <div class="toolbar">
      <input type="color" v-model="color" title="画笔颜色" />
      <input
        type="range"
        v-model.number="size"
        min="1"
        max="30"
        title="画笔大小"
      />
      <button @click="clearCanvas">🗑️ 清空</button>
      <button @click="undo" :disabled="undoStack.length === 0">↩️ 撤销</button>
    </div>
    <div class="status">
      当前词语：<strong>{{ word || '未开始' }}</strong>
      <span class="drawer-info" v-if="drawerName">画手：{{ drawerName }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  word: String,
  drawerName: String
})

const emit = defineEmits(['draw'])

const canvasRef = ref(null)
const color = ref('#000000')
const size = ref(3)
const ctx = ref(null)

const drawing = ref(false)
const lastPos = ref({ x: 0, y: 0 })

const undoStack = ref([])
const redoStack = ref([])

onMounted(() => {
  const canvas = canvasRef.value
  ctx.value = canvas.getContext('2d')
  ctx.value.fillStyle = '#fff'
  ctx.value.fillRect(0, 0, canvas.width, canvas.height)

  canvas.addEventListener('mousedown', startDrawing)
  canvas.addEventListener('mousemove', draw)
  canvas.addEventListener('mouseup', stopDrawing)
  canvas.addEventListener('mouseleave', stopDrawing)

  // 触摸支持
  canvas.addEventListener('touchstart', handleTouchStart)
  canvas.addEventListener('touchmove', handleTouchMove)
  canvas.addEventListener('touchend', stopDrawing)
})

onUnmounted(() => {
  const canvas = canvasRef.value
  canvas?.removeEventListener('mousedown', startDrawing)
  canvas?.removeEventListener('mousemove', draw)
  canvas?.removeEventListener('mouseup', stopDrawing)
  canvas?.removeEventListener('mouseleave', stopDrawing)
})

function startDrawing(e) {
  drawing.value = true
  const pos = getMousePos(e)
  lastPos.value = pos
  // 保存当前画布状态
  undoStack.value.push(canvasRef.value.toDataURL())
}

function draw(e) {
  if (!drawing.value) return
  const pos = getMousePos(e)

  const ctx2 = ctx.value
  ctx2.beginPath()
  ctx2.moveTo(lastPos.value.x, lastPos.value.y)
  ctx2.lineTo(pos.x, pos.y)
  ctx2.strokeStyle = color.value
  ctx2.lineWidth = size.value
  ctx2.lineCap = 'round'
  ctx2.stroke()

  lastPos.value = pos
}

function stopDrawing() {
  if (drawing.value) {
    drawing.value = false
    redoStack.value = []
  }
}

function clearCanvas() {
  undoStack.value.push(canvasRef.value.toDataURL())
  const canvas = canvasRef.value
  const context = ctx.value
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#fff'
  context.fillRect(0, 0, canvas.width, canvas.height)
}

function undo() {
  if (undoStack.value.length === 0) return
  redoStack.value.push(canvasRef.value.toDataURL())
  const prevState = undoStack.value.pop()
  if (prevState) {
    const img = new Image()
    img.onload = () => {
      ctx.value.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
      ctx.value.drawImage(img, 0, 0)
    }
    img.src = prevState
  }
}

function getMousePos(e) {
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
}

function handleTouchStart(e) {
  e.preventDefault()
  const touch = e.touches[0]
  startDrawing({ clientX: touch.clientX, clientY: touch.clientY })
}

function handleTouchMove(e) {
  e.preventDefault()
  const touch = e.touches[0]
  draw({ clientX: touch.clientX, clientY: touch.clientY })
}
</script>

<style scoped>
.game-canvas-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.draw-canvas {
  border: 2px solid #ddd;
  border-radius: 8px;
  background: white;
  touch-action: none;
  cursor: crosshair;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.toolbar input[type='color'] {
  width: 40px;
  height: 32px;
  border: none;
  cursor: pointer;
}

.toolbar input[type='range'] {
  width: 100px;
}

.toolbar button {
  padding: 0.5rem 1rem;
  background: #f0f0f0;
  border: 1px solid #ccc;
  border-radius: 4px;
  cursor: pointer;
}

.toolbar button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.status {
  font-size: 1.2rem;
}

.status strong {
  color: #42b983;
}

.drawer-info {
  margin-left: 1rem;
  color: #888;
  font-size: 0.9rem;
}
</style>
