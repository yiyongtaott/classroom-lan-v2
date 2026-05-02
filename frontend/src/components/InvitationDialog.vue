<template>
  <!-- 完整对话框 -->
  <div v-if="invitation && invitation.state === 'PENDING' && !minimized" class="modal-backdrop">
    <div class="modal">
      <h3>🎮 游戏邀请</h3>
      <p class="who">
        <strong>{{ initiatorName }}</strong> 邀请你玩 <strong>{{ gameTitle }}</strong>
      </p>
      <p class="status-line">
        <span>已接受 {{ counts.accept }}</span> ·
        <span>已拒绝 {{ counts.decline }}</span> ·
        <span>等待 {{ counts.pending }}</span>
        <span class="timer"> · ⏱ {{ timeLeft }}s</span>
      </p>
      <div class="actions">
        <button class="btn primary" @click="respond('ACCEPT')" :disabled="myResponse === 'ACCEPT'">
          {{ myResponse === 'ACCEPT' ? '已接受' : '接受' }}
        </button>
        <button class="btn" @click="respond('DECLINE')" :disabled="myResponse === 'DECLINE'">
          {{ myResponse === 'DECLINE' ? '已拒绝' : '拒绝' }}
        </button>
        <button class="btn warn" @click="respond('FORCE')">强制进入</button>
      </div>
      <button class="minimize" @click="minimized = true" title="最小化">— 最小化</button>
    </div>
  </div>

  <!-- 最小化的悬浮 chip -->
  <button v-if="invitation && invitation.state === 'PENDING' && minimized"
          class="floater"
          @click="minimized = false">
    🎮 邀请待处理 ({{ counts.accept }}/{{ counts.total }})
  </button>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoomStore } from '../stores/room'
import { useStomp } from '../composables/useStomp'
import { useToastStore } from '../stores/toast'
import { useRouter } from 'vue-router'
import { TOPIC, APP } from '../appConfig'

const roomStore = useRoomStore()
const stomp = useStomp()
const toastStore = useToastStore()
const router = useRouter()

const minimized = ref(false)
const now = ref(Date.now())
let unsub = null
let tickTimer = null

const invitation = computed(() => roomStore.invitation)

const counts = computed(() => {
  const inv = invitation.value
  const players = roomStore.players
  if (!inv) return { accept: 0, decline: 0, pending: 0, total: 0 }
  let accept = 0, decline = 0, pending = 0
  for (const p of players) {
    if (p.status !== 'ONLINE') continue
    const r = inv.responses ? inv.responses[p.id] : null
    if (r === 'ACCEPT' || r === 'FORCE') accept++
    else if (r === 'DECLINE') decline++
    else pending++
  }
  return { accept, decline, pending, total: accept + decline + pending }
})

const timeLeft = computed(() => {
  const inv = invitation.value
  if (!inv) return 0
  const remain = Math.max(0, Math.floor((inv.startTime + 30000 - now.value) / 1000))
  return remain
})

const myResponse = computed(() =>
  invitation.value?.responses?.[roomStore.self?.id] || null
)

const initiatorName = computed(() => {
  const inv = invitation.value
  if (!inv) return ''
  const p = roomStore.players.find(x => x.id === inv.initiatorPlayerId)
  return p ? p.name : '某玩家'
})

const gameTitle = computed(() => {
  const map = { NUMBER_GUESS: '猜数字', DRAW: '你画我猜', QUIZ: '抢答' }
  return map[invitation.value?.gameType] || invitation.value?.gameType || '游戏'
})

function respond(action) {
  if (!roomStore.self) return
  stomp.publish(APP.GAME_INVITATION_RESPOND, {
    playerId: roomStore.self.id,
    response: action
  })
}

watch(() => invitation.value?.state, (state, oldState) => {
  if (state === 'ACTIVE') {
    minimized.value = false
    toastStore.push({ type: 'ok', icon: '🎮', title: '游戏开始', body: gameTitle.value })
    router.push('/game')
  } else if (state === 'CANCELLED' && oldState === 'PENDING') {
    minimized.value = false
    toastStore.push({ type: 'warn', icon: '🚫', title: '邀请已取消', body: gameTitle.value })
  } else if (state === 'PENDING') {
    minimized.value = false
  }
})

onMounted(() => {
  unsub = stomp.subscribe(TOPIC.GAME_INVITATION, (inv) => {
    roomStore.setInvitation(inv)
  })
  // 时钟驱动倒计时显示
  tickTimer = setInterval(() => { now.value = Date.now() }, 1000)
})

onBeforeUnmount(() => {
  if (unsub) unsub()
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.45);
  z-index: 9000;
  display: flex; align-items: center; justify-content: center;
}
.modal {
  background: white;
  border-radius: 10px;
  padding: 1.5rem 1.75rem;
  max-width: 420px; width: 90%;
  box-shadow: 0 20px 60px rgba(0,0,0,.25);
  position: relative;
}
.modal h3 { margin: 0 0 .5rem; color: #16a34a; }
.who { color: #1f2937; }
.status-line { color: #6b7280; font-size: .85rem; }
.timer { color: #f59e0b; }
.actions { display: flex; gap: .5rem; margin-top: 1rem; }
.btn { flex: 1; padding: .55rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.warn { background: #f59e0b; color: white; border-color: #f59e0b; }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.minimize {
  position: absolute; top: .5rem; right: .65rem;
  background: transparent; border: none; color: #6b7280; cursor: pointer; font-size: .8rem;
}
.minimize:hover { color: #1f2937; }

.floater {
  position: fixed; bottom: 1.5rem; right: 1.5rem; z-index: 8000;
  padding: .65rem 1rem; border-radius: 999px;
  background: #16a34a; color: white; border: none;
  cursor: pointer; box-shadow: 0 6px 20px rgba(22,163,74,.45);
  font-size: .9rem;
}
.floater:hover { background: #15803d; }
</style>
