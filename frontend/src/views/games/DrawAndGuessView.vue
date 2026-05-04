<template>
  <GameBaseView title="🎨 你画我猜" @stop="stop">
    <section class="draw-and-guess">
      <header class="board-header">
        <div class="meta">
          <span v-if="phase === 'SELECTING' && isDrawer">请从 3 个候选词中选 1 个</span>
          <span v-else-if="phase === 'SELECTING'">等待 <strong>{{ drawerName }}</strong> 选词</span>
          <span v-else-if="phase === 'DRAWING'">
            <strong>{{ drawerName }}</strong> 在画
            <span v-if="!isDrawer && hint">（{{ hint }}）</span>
            <span v-if="timeLeft > 0">· ⏱ {{ timeLeft }}s</span>
          </span>
          <span v-else-if="phase === 'ROUND_END'">揭晓答案中...</span>
          <span v-else-if="phase === 'GAME_OVER'">游戏结束</span>
        </div>
      </header>

    <div class="layout">
      <!-- 左侧：候选词或画布 -->
      <div class="canvas-area">
        <div v-if="phase === 'SELECTING' && isDrawer" class="word-options">
          <div class="prompt">请选一个词来画：</div>
          <button v-for="(w, i) in wordOptions" :key="i" class="opt" @click="selectWord(i)">{{ w }}</button>
        </div>

        <div v-else class="canvas-host">
          <canvas ref="canvasEl" width="640" height="480" @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointerleave="onUp"></canvas>
          <div v-if="isDrawer && phase === 'DRAWING'" class="canvas-tools">
            <label>颜色 <input type="color" v-model="color" /></label>
            <label>粗细 <input type="range" min="1" max="20" v-model.number="lineWidth" /></label>
            <button class="btn small" :class="{ active: tool === 'pen' }" @click="tool = 'pen'">画笔</button>
            <button class="btn small" :class="{ active: tool === 'eraser' }" @click="tool = 'eraser'">橡皮</button>
            <button class="btn small danger" @click="clearCanvas">清空</button>
          </div>
        </div>
      </div>

      <!-- 右侧：得分 + 猜词 -->
      <aside class="side">
        <div class="scores">
          <h4>得分</h4>
          <ul>
            <li v-for="p in scoreList" :key="p.id">
              <Avatar :player="p" :size="24" />
              <span class="name">{{ p.name }}<span v-if="p.id === currentDrawerId" class="tag">画图</span></span>
              <span class="score">{{ p.score }}</span>
            </li>
          </ul>
        </div>

        <div v-if="!isDrawer" class="guess-box">
          <h4>猜词</h4>
          <form @submit.prevent="submitGuess">
            <input v-model="guessText" placeholder="输入你的答案" :disabled="phase !== 'DRAWING'" />
            <button class="btn primary" :disabled="!guessText.trim() || phase !== 'DRAWING'">提交</button>
          </form>
          <ul class="guess-history">
            <li v-for="(g, i) in guessHistory" :key="i" :class="{ correct: g.correct }">
              <strong>{{ g.guesserName }}</strong>:
              <span v-if="g.correct">猜中！</span>
              <span v-else>{{ g.guess }}</span>
            </li>
          </ul>
        </div>

        <div v-else class="drawer-hint">
          <h4>你的词</h4>
          <div class="word">{{ secretWord || '—' }}</div>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup>
import {computed, ref, watch, onMounted, onBeforeUnmount, watchEffect} from 'vue'
import { useRoomStore } from '../../stores/room'
import { useUserListStore } from '../../stores/userList'
import { useStomp } from '../../composables/useStomp'
import { APP } from '../../appConfig'
import Avatar from '../../components/Avatar.vue'
import GameBaseView from '../../components/GameBaseView.vue'

const roomStore = useRoomStore()
const userList = useUserListStore()
const stomp = useStomp()

const canvasEl = ref(null)
const color = ref('#1f2937')
const lineWidth = ref(4)
const tool = ref('pen')
const guessText = ref('')
const drawing = ref(false)
const lastPoint = ref(null)
const buffer = ref([])
const guessHistory = ref([])
const phase = ref('WAITING')
const wordOptions = ref([])
const currentDrawerId = ref(null)
const drawerName = ref('')
const secretWord = ref('')
const hint = ref('')
const roundEndTs = ref(0)
const now = ref(Date.now())
const scoresMap = ref({})
let tickTimer = null
let rafPending = false
const renderQueue = []

const me = computed(() => roomStore.self)
const isDrawer = computed(() => me.value?.id === currentDrawerId.value)

const timeLeft = computed(() => {
  if (!roundEndTs.value) return 0
  return Math.max(0, Math.floor((roundEndTs.value - now.value) / 1000))
})

const scoreList = computed(() =>
  userList.users.map(u => ({ ...u, score: scoresMap.value[u.id] || 0 }))
    .sort((a, b) => b.score - a.score)
)

function selectWord(idx) {
  stomp.publish(APP.DRAW_SELECT, { index: idx })
}

function stop() {
  stomp.publish(APP.GAME_STOP, {})
}

function clearCanvas() {
  if (!isDrawer.value) return
  stomp.publish(APP.DRAW_CLEAR, {})
  doClearCanvas()
}

function doClearCanvas() {
  const canvas = canvasEl.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
}

function pos(ev) {
  const r = canvasEl.value.getBoundingClientRect()
  return { x: (ev.clientX - r.left) * canvasEl.value.width / r.width, y: (ev.clientY - r.top) * canvasEl.value.height / r.height }
}

function onMove(ev) {
  if (!drawing.value || !isDrawer.value) return
  const p = pos(ev)
  drawSegmentLocal(lastPoint.value, p)
  buffer.value.push(p)
  lastPoint.value = p
  if (buffer.value.length >= 12) {
    flushStrokes()
  }
}

let strokeTimer = null
function startStrokeTimer() {
  if (strokeTimer) return
  strokeTimer = setInterval(() => {
    if (drawing.value) flushStrokes()
  }, 50)
}

function onUp() {
  if (drawing.value) {
    flushStrokes()
  }
  drawing.value = false
  lastPoint.value = null
  if (strokeTimer) {
    clearInterval(strokeTimer)
    strokeTimer = null
  }
}

function onDown(ev) {
  if (!isDrawer.value || phase.value !== 'DRAWING') return
  drawing.value = true
  lastPoint.value = pos(ev)
  buffer.value = [lastPoint.value]
  startStrokeTimer()
}

function flushStrokes() {
  if (!buffer.value.length) return
  stomp.publish(APP.DRAW_STROKE, {
    points: buffer.value,
    color: color.value,
    lineWidth: lineWidth.value,
    tool: tool.value
  })
  buffer.value = []
}

function drawSegmentLocal(a, b) {
  if (!a || !b) return
  const ctx = canvasEl.value.getContext('2d')
  ctx.lineCap = 'round'
  ctx.lineWidth = lineWidth.value
  ctx.strokeStyle = tool.value === 'eraser' ? '#ffffff' : color.value
  ctx.globalCompositeOperation = tool.value === 'eraser' ? 'destination-out' : 'source-over'
  ctx.beginPath()
  ctx.moveTo(a.x, a.y)
  ctx.lineTo(b.x, b.y)
  ctx.stroke()
}

function applyStroke(stroke) {
  if (!stroke) return
  if (stroke.type === 'CLEAR') {
    doClearCanvas()
    return
  }
  if (!stroke.points || !stroke.points.length) return
  // 自己发的笔画也会回流，但为了避免重影，drawer 在本地已经画过了，这里直接 skip
  if (isDrawer.value) return
  const ctx = canvasEl.value.getContext('2d')
  ctx.lineCap = 'round'
  ctx.lineWidth = stroke.lineWidth || 4
  ctx.strokeStyle = stroke.tool === 'eraser' ? '#ffffff' : (stroke.color || '#000000')
  ctx.globalCompositeOperation = stroke.tool === 'eraser' ? 'destination-out' : 'source-over'
  const pts = stroke.points
  ctx.beginPath()
  ctx.moveTo(pts[0].x, pts[0].y)
  for (let i = 1; i < pts.length; i++) {
    ctx.lineTo(pts[i].x, pts[i].y)
  }
  ctx.stroke()
}

function flushRender() {
  rafPending = false
  while (renderQueue.length) {
    applyStroke(renderQueue.shift())
  }
}

function enqueueStroke(s) {
  renderQueue.push(s)
  if (!rafPending) {
    rafPending = true
    requestAnimationFrame(flushRender)
  }
}

function submitGuess() {
  const t = guessText.value.trim()
  if (!t) return
  stomp.publish(APP.DRAW_GUESS, { guess: t })
  guessText.value = ''
}

function applyGameState(state) {
  if (!state || state.gameType !== 'DRAW') return
  const data = state.data || {}
  switch (state.stage) {
    case 'SELECTING':
      phase.value = 'SELECTING'
      currentDrawerId.value = data.drawerId
      drawerName.value = data.drawerName || ''
      doClearCanvas()
      break
    case 'ROUND_START':
      phase.value = 'DRAWING'
      currentDrawerId.value = data.drawerId
      drawerName.value = data.drawerName || ''
      hint.value = `${data.wordLength || '?'} 字`
      roundEndTs.value = data.roundEndTs || 0
      doClearCanvas()
      guessHistory.value = []
      break
    case 'GUESS_WRONG':
      guessHistory.value.push({
        guesserName: data.guesserName,
        guess: data.guess,
        correct: false
      })
      break
    case 'GUESS_CORRECT':
      guessHistory.value.push({
        guesserName: data.guesserName,
        correct: true
      })
      if (data.scores) scoresMap.value = data.scores
      break
    case 'ROUND_END':
      phase.value = 'ROUND_END'
      hint.value = `答案是 ${data.word}`
      if (data.scores) scoresMap.value = data.scores
      break
    case 'GAME_OVER':
      phase.value = 'GAME_OVER'
      if (data.scores) scoresMap.value = data.scores
      break
  }
}

function applyDrawPrivate(msg) {
  if (!msg) return
  if (msg.type === 'WORD_OPTIONS') {
    wordOptions.value = [...(msg.options || [])]
    console.log('[Draw] wordOptions assigned:', wordOptions.value)
  } else if (msg.type === 'WORD_REVEAL') {
    secretWord.value = msg.word
  }
}

watchEffect(() => {
  if (roomStore.lastGameState) {
    applyGameState(roomStore.lastGameState)
  }
})
watch(() => roomStore.drawStrokes.length, (newLen, oldLen) => {
  // 只对最新到达的笔画处理
  const added = roomStore.drawStrokes.slice(oldLen || 0)
  for (const s of added) enqueueStroke(s)
})
watch(() => roomStore.drawPrivate, (m) => applyDrawPrivate(m))

onMounted(() => {
  tickTimer = setInterval(() => { now.value = Date.now() }, 500)
  // 已经在 store 里的笔画补绘
  for (const s of roomStore.drawStrokes) enqueueStroke(s)
})

onBeforeUnmount(() => {
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.draw-and-guess { max-width: 100%; }
.board-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; gap: 1rem; flex-wrap: wrap; }
.board-header h2 { margin: 0; color: #16a34a; }
.meta { color: #6b7280; font-size: .9rem; display: inline-flex; gap: .75rem; align-items: center; }

.layout { display: grid; grid-template-columns: 1fr 280px; gap: 1rem; }
.canvas-area { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: .75rem; }
.canvas-host canvas {
  display: block; width: 100%; height: auto; aspect-ratio: 4/3;
  border: 1px solid #e5e7eb; background: white; border-radius: 4px;
  touch-action: none;
}
.canvas-tools {
  display: flex; gap: .5rem; align-items: center; flex-wrap: wrap;
  padding: .5rem 0;
}
.canvas-tools label { display: inline-flex; align-items: center; gap: .25rem; font-size: .85rem; color: #6b7280; }
.canvas-tools .btn.active { background: #16a34a; color: white; border-color: #16a34a; }

.word-options { padding: 2rem; text-align: center; }
.word-options .prompt { color: #6b7280; margin-bottom: 1rem; }
.word-options .opt {
  display: inline-block; margin: .5rem; padding: .75rem 1.5rem;
  background: #f0fdf4; border: 2px solid #bbf7d0; color: #15803d;
  border-radius: 8px; font-size: 1.05rem; font-weight: 600; cursor: pointer;
}
.word-options .opt:hover { background: #dcfce7; transform: translateY(-2px); }

.side { display: flex; flex-direction: column; gap: 1rem; }
.scores, .guess-box, .drawer-hint {
  background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: .75rem;
}
.scores h4, .guess-box h4, .drawer-hint h4 { margin: 0 0 .5rem; color: #374151; font-size: .9rem; }
.scores ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: .35rem; }
.scores li { display: flex; align-items: center; gap: .35rem; font-size: .85rem; }
.scores .name { flex: 1; }
.scores .tag { color: #f59e0b; font-size: .65rem; margin-left: .35rem; }
.scores .score { font-weight: 700; color: #16a34a; }

.guess-box form { display: flex; gap: .35rem; }
.guess-box input { flex: 1; padding: .35rem; border: 1px solid #d1d5db; border-radius: 4px; }
.guess-history { list-style: none; padding: 0; margin: .5rem 0 0; max-height: 180px; overflow-y: auto; font-size: .85rem; }
.guess-history li { padding: .25rem 0; }
.guess-history li.correct { color: #16a34a; font-weight: 600; }

.drawer-hint .word {
  font-size: 1.4rem; font-weight: 700; text-align: center;
  padding: .65rem; background: #fef3c7; border-radius: 6px; color: #92400e;
}

.btn { padding: .35rem .75rem; border-radius: 4px; border: 1px solid #d1d5db; background: white; cursor: pointer; }
.btn.small { padding: .2rem .55rem; font-size: .8rem; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }

@media (max-width: 800px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
