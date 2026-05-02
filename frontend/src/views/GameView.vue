<template>
  <section class="game">
    <header>
      <h2>游戏</h2>
      <div class="active" v-if="active">
        当前游戏：<strong>{{ gameTitle(active) }}</strong>
        <button class="btn small" @click="stopGame">停止</button>
      </div>
      <div class="active" v-else-if="invitation && invitation.state === 'PENDING'">
        正在等待玩家响应：<strong>{{ gameTitle(invitation.gameType) }}</strong>
      </div>
      <div class="active" v-else>当前空闲</div>
    </header>

    <div v-if="!active" class="lobby">
      <h3>选择游戏发起邀请</h3>
      <p class="hint">点击后将向所有在线玩家发出邀请，全员接受才会进入游戏；任一玩家强制进入会立即开始。</p>
      <div class="games">
        <button class="game-card" @click="startGame('NUMBER_GUESS')" :disabled="!stomp.connected.value">
          <div class="title">🎯 猜数字</div>
          <div class="desc">范围 1–100，最快猜中获胜</div>
        </button>
        <button class="game-card disabled" disabled>
          <div class="title">🎨 你画我猜</div>
          <div class="desc">扩展示例（预留）</div>
        </button>
        <button class="game-card disabled" disabled>
          <div class="title">📝 抢答</div>
          <div class="desc">扩展示例（预留）</div>
        </button>
      </div>

      <div v-if="historyEntries.length" class="history">
        <h3>历史记录 <small>（共 {{ historyEntries.length }} 条）</small></h3>
        <ul class="log static">
          <li v-for="(item, i) in historyEntries.slice().reverse()" :key="i" :class="String(item.stage || '').toLowerCase()">
            <span class="stage">{{ stageLabel(item.stage) }}</span>
            <span v-if="item.data?.playerName">{{ item.data.playerName }}</span>
            <span v-if="item.data?.value !== undefined">猜 {{ item.data.value }}</span>
            <span class="ts" v-if="item.ts">{{ tsLabel(item.ts) }}</span>
          </li>
        </ul>
        <button class="btn small danger" @click="clearHistory">清空历史</button>
      </div>
    </div>

    <div v-else-if="active === 'NUMBER_GUESS'" class="board">
      <div class="hint">
        <div>{{ feedback || '猜一个 1–100 之间的整数' }}</div>
        <div class="rounds">回合：{{ rounds }}</div>
      </div>
      <form class="input" @submit.prevent="guess">
        <input v-model.number="value" type="number" min="1" max="100" :disabled="winner !== ''" />
        <button class="btn primary" :disabled="winner !== '' || !Number.isInteger(value)">提交</button>
      </form>

      <div v-if="winner" class="winner">🏆 {{ winner }} 猜中！点击"停止"开始下一局。</div>

      <div class="log-box">
        <div class="aside-title">动作记录</div>
        <ul class="log">
          <li v-for="(item, i) in liveLog" :key="i" :class="String(item.stage || '').toLowerCase()">
            <span class="stage">{{ stageLabel(item.stage) }}</span>
            <span v-if="item.data?.playerName">{{ item.data.playerName }}</span>
            <span v-if="item.data?.value !== undefined">猜 {{ item.data.value }}</span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoomStore } from '../stores/room'
import { useStomp } from '../composables/useStomp'
import { TOPIC, APP, API_BASE } from '../appConfig'

const roomStore = useRoomStore()
const stomp = useStomp()

const value = ref(50)
const liveLog = ref([])
const winner = ref('')
const rounds = ref(0)
const feedback = ref('')

const active = computed(() => roomStore.gameType)
const invitation = computed(() => roomStore.invitation)
const historyEntries = computed(() =>
  (roomStore.gameLog || []).filter(x => x && x.gameType === 'NUMBER_GUESS')
)

let unsub = null

function stageLabel(stage) {
  const map = { STARTED: '开局', LOW: '太小', HIGH: '太大', WIN: '猜中', STOPPED: '结束', INVALID: '无效' }
  return map[stage] || stage || '—'
}

function gameTitle(t) {
  const map = { NUMBER_GUESS: '猜数字', DRAW: '你画我猜', QUIZ: '抢答' }
  return map[t] || t
}

function tsLabel(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

function startGame(type) {
  liveLog.value = []
  winner.value = ''
  feedback.value = ''
  rounds.value = 0
  // 走邀请流程：服务端会 broadcast 到 /topic/game.invitation，全局对话框接管
  stomp.publish(APP.GAME_START, { type, playerId: roomStore.self?.id })
}

function stopGame() {
  stomp.publish(APP.GAME_STOP, {})
}

function guess() {
  if (!Number.isInteger(value.value)) return
  stomp.publish(APP.GAME_ACTION, {
    action: 'GUESS',
    value: value.value,
    playerId: roomStore.self?.id
  })
}

async function clearHistory() {
  if (!confirm('清空所有游戏历史？')) return
  await fetch(`${API_BASE}/game/history`, { method: 'DELETE' })
  await roomStore.loadGameHistory()
}

function applyState(state) {
  liveLog.value.push(state)
  if (liveLog.value.length > 50) liveLog.value.shift()
  if (state.stage === 'WIN') {
    winner.value = state.data?.playerName || '某玩家'
    rounds.value = state.data?.rounds || rounds.value
    feedback.value = `${winner.value} 在第 ${rounds.value} 回合猜中！`
  } else if (state.stage === 'LOW') {
    rounds.value = state.data?.rounds || rounds.value
    feedback.value = `${state.data?.playerName} 猜了 ${state.data?.value}：太小`
  } else if (state.stage === 'HIGH') {
    rounds.value = state.data?.rounds || rounds.value
    feedback.value = `${state.data?.playerName} 猜了 ${state.data?.value}：太大`
  } else if (state.stage === 'STARTED') {
    feedback.value = '游戏开始，范围 ' + (state.data?.range || '1-100')
    winner.value = ''
    rounds.value = 0
  } else if (state.stage === 'STOPPED') {
    feedback.value = '游戏已结束'
  }
}

onMounted(async () => {
  await Promise.all([
    roomStore.refreshStatus(),
    roomStore.loadGameHistory()
  ])
  unsub = stomp.subscribe(TOPIC.GAME_STATE, (state) => {
    roomStore.setGameState(state)
    applyState(state)
  })
})

onBeforeUnmount(() => {
  if (unsub) unsub()
})
</script>

<style scoped>
.game { max-width: 900px; margin: 0 auto; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; flex-wrap: wrap; gap: .5rem; }
header h2 { margin: 0; color: #16a34a; }
.active { color: #6b7280; font-size: .9rem; }
.active strong { color: #1f2937; margin-right: .5rem; }
.lobby h3 { color: #374151; }
.lobby h3 small { color: #9ca3af; font-weight: normal; font-size: .85rem; }
.hint { color: #6b7280; font-size: .9rem; margin-bottom: 1rem; }
.games { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.game-card { background: white; border: 2px solid #e5e7eb; border-radius: 8px; padding: 1.25rem; text-align: left; cursor: pointer; transition: all .15s; }
.game-card:hover:not(.disabled):not(:disabled) { border-color: #16a34a; transform: translateY(-2px); }
.game-card.disabled, .game-card:disabled { opacity: .5; cursor: not-allowed; }
.game-card .title { font-weight: 700; font-size: 1.05rem; margin-bottom: .35rem; }
.game-card .desc { color: #6b7280; font-size: .85rem; }

.history { margin-top: 2rem; }
.board { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 1.25rem; }
.hint { display: flex; justify-content: space-between; align-items: center; padding: 1rem; background: #f9fafb; border-radius: 6px; margin-bottom: 1rem; }
.rounds { color: #6b7280; font-size: .9rem; }
.input { display: flex; gap: .5rem; }
.input input { flex: 1; padding: .55rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1.1rem; }
.btn { padding: .5rem 1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; }
.btn.small { padding: .25rem .6rem; font-size: .8rem; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
.winner { margin-top: 1rem; padding: .75rem; background: #fef3c7; border: 1px solid #fde68a; border-radius: 6px; color: #92400e; }
.log-box { margin-top: 1.25rem; }
.aside-title { color: #6b7280; font-size: .8rem; text-transform: uppercase; }
.log { list-style: none; padding: 0; margin: .5rem 0; max-height: 240px; overflow-y: auto; border: 1px solid #f3f4f6; border-radius: 6px; background: white; }
.log.static { max-height: 320px; }
.log li { display: flex; gap: .5rem; padding: .35rem .75rem; border-bottom: 1px solid #f3f4f6; font-size: .9rem; align-items: center; }
.log li:last-child { border-bottom: none; }
.log li .stage { width: 60px; font-weight: 600; flex-shrink: 0; }
.log li .ts { margin-left: auto; color: #9ca3af; font-size: .75rem; font-family: ui-monospace, monospace; }
.log li.low .stage { color: #2563eb; }
.log li.high .stage { color: #dc2626; }
.log li.win .stage { color: #16a34a; }
.log li.started .stage { color: #6b7280; }
.log li.stopped .stage { color: #6b7280; }
@media (max-width: 720px) { .games { grid-template-columns: 1fr; } }
</style>
