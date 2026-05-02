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

      <!-- 实时投票头像列表 -->
      <ul class="vote-list">
        <li v-for="p in onlinePlayers" :key="p.id" :class="responseFor(p.id)">
          <Avatar :player="p" :size="22" />
          <span class="name">{{ p.name }}</span>
          <span class="status">{{ responseLabel(responseFor(p.id)) }}</span>
        </li>
      </ul>

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
import { computed, ref, watch, onBeforeUnmount, onMounted } from 'vue'
import { useRoomStore } from '../stores/room'
import { useUserListStore } from '../stores/userList'
import { useStomp } from '../composables/useStomp'
import { useToastStore } from '../stores/toast'
import { useRouter } from 'vue-router'
import { APP } from '../appConfig'
import Avatar from './Avatar.vue'

const roomStore = useRoomStore()
const userList = useUserListStore()
const stomp = useStomp()
const toastStore = useToastStore()
const router = useRouter()

const minimized = ref(false)
const now = ref(Date.now())
let tickTimer = null

const invitation = computed(() => roomStore.invitation)
const onlinePlayers = computed(() =>
  userList.users.filter(u => (u.status || 'ONLINE') === 'ONLINE' || u.wsAlive)
)

function responseFor(playerId) {
  return invitation.value?.responses?.[playerId] || 'PENDING'
}

function responseLabel(r) {
  const map = { ACCEPT: '已接受', DECLINE: '已拒绝', FORCE: '强制', PENDING: '等待中' }
  return map[r] || '等待中'
}

const counts = computed(() => {
  const inv = invitation.value
  if (!inv) return { accept: 0, decline: 0, pending: 0, total: 0 }
  let accept = 0, decline = 0, pending = 0
  for (const p of onlinePlayers.value) {
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
  const p = userList.find(inv.initiatorPlayerId)
  return p ? p.name : '某玩家'
})

const gameTitle = computed(() => {
  const map = { NUMBER_GUESS: '猜数字', DRAW: '你画我猜', QUIZ: '抢答' }
  return map[invitation.value?.gameType] || invitation.value?.gameType || '游戏'
})

function respond(action) {
  if (!roomStore.self) return
  // 本地立即反映（不等服务端回包，无延迟）
  if (invitation.value && invitation.value.responses) {
    invitation.value.responses[roomStore.self.id] = action
  }
  stomp.publish(APP.GAME_INVITATION_RESPOND, {
    playerId: roomStore.self.id,
    response: action
  })
}

watch(() => invitation.value?.state, (state, oldState) => {
  if (state === 'ACTIVE') {
    minimized.value = false
    toastStore.push({ type: 'ok', icon: '🎮', title: '游戏开始', body: gameTitle.value })
    if (roomStore.isParticipantInActiveGame || !roomStore.gameStartInfo) {
      router.push('/game')
    }
  } else if (state === 'CANCELLED' && oldState === 'PENDING') {
    minimized.value = false
    toastStore.push({ type: 'warn', icon: '🚫', title: '邀请已取消', body: gameTitle.value })
  } else if (state === 'PENDING') {
    minimized.value = false
  }
})

onMounted(() => {
  tickTimer = setInterval(() => { now.value = Date.now() }, 500)
})

onBeforeUnmount(() => {
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
  max-width: 460px; width: 90%;
  box-shadow: 0 20px 60px rgba(0,0,0,.25);
  position: relative;
}
.modal h3 { margin: 0 0 .5rem; color: #16a34a; }
.who { color: #1f2937; }
.status-line { color: #6b7280; font-size: .85rem; }
.timer { color: #f59e0b; }

.vote-list {
  list-style: none; padding: 0; margin: .85rem 0;
  border: 1px solid #f3f4f6; border-radius: 6px;
  max-height: 200px; overflow-y: auto;
}
.vote-list li {
  display: flex; align-items: center; gap: .55rem;
  padding: .35rem .7rem; border-bottom: 1px solid #f9fafb;
}
.vote-list li:last-child { border-bottom: none; }
.vote-list .name { flex: 1; }
.vote-list .status { font-size: .75rem; padding: .12rem .5rem; border-radius: 999px; }
.vote-list li.ACCEPT .status, .vote-list li.FORCE .status { background: #dcfce7; color: #15803d; }
.vote-list li.DECLINE .status { background: #fee2e2; color: #991b1b; }
.vote-list li.PENDING .status { background: #f3f4f6; color: #6b7280; }

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
