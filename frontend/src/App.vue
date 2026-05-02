<template>
  <div id="app">
    <header class="app-bar">
      <div class="brand">📡 ClassroomLAN <span class="ver">v2</span></div>
      <nav class="nav">
        <router-link to="/">主页</router-link>
        <router-link to="/join" v-if="!roomStore.hasJoined">加入</router-link>
        <router-link to="/game" v-if="roomStore.hasJoined">游戏</router-link>
        <router-link to="/files" v-if="roomStore.hasJoined">文件</router-link>
      </nav>
      <div class="status-pill">
        <span :class="['dot', stomp.connected.value ? 'on' : 'off']" />
        {{ stomp.connected.value ? '在线' : '离线' }}
        · {{ roomStore.isHost ? 'HOST' : 'CUSTOMER' }}
        · {{ roomStore.nodeId || '—' }}
        <span v-if="roomStore.hostname"> ({{ roomStore.hostname }})</span>
      </div>
    </header>

    <div :class="['app-body', { joined: roomStore.hasJoined }]">
      <ChatPanel v-if="roomStore.hasJoined" class="left-col" />
      <main class="center-col">
        <router-view />
      </main>
      <SettingsPanel v-if="roomStore.hasJoined" class="right-col" />
    </div>

    <ToastHost />
    <InvitationDialog />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoomStore } from './stores/room'
import { useToastStore } from './stores/toast'
import { useStomp } from './composables/useStomp'
import { TOPIC, APP } from './appConfig'
import { useRouter } from 'vue-router'

import ChatPanel from './components/ChatPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import ToastHost from './components/ToastHost.vue'
import InvitationDialog from './components/InvitationDialog.vue'

const roomStore = useRoomStore()
const toastStore = useToastStore()
const stomp = useStomp()
const router = useRouter()

let pollTimer = null
let unsubChat = null
let unsubPlayers = null
let unsubFile = null

// Bug 11: 非聊天页有新消息 / 非文件页有上传 → toast
function isOnPath(prefix) {
  return router.currentRoute.value.path.startsWith(prefix)
}

onMounted(async () => {
  stomp.connect()

  try {
    await roomStore.refreshStatus()
  } catch {}

  // 启动时尝试自动签到（同 IP 命中 → 不需要再 /join）
  await roomStore.bootstrap()
  if (roomStore.hasJoined) {
    await roomStore.refreshSnapshot()
    await roomStore.loadChatHistory()
    await roomStore.loadGameHistory()
  }

  // 周期轮询
  pollTimer = setInterval(async () => {
    try {
      await roomStore.refreshStatus()
      if (roomStore.hasJoined) await roomStore.refreshSnapshot()
    } catch {}
  }, 4000)

  // 玩家列表实时推送（Bug 7：连接事件 / 清理触发）
  unsubPlayers = stomp.subscribe(TOPIC.PLAYERS, (list) => {
    roomStore.setPlayers(list)
    if (roomStore.self) {
      const updated = list.find(p => p.id === roomStore.self.id)
      if (updated) roomStore.setSelf({ ...roomStore.self, ...updated })
    }
  })

  // Bug 11：非 chat 路由下有新消息 → toast 提醒
  unsubChat = stomp.subscribe(TOPIC.CHAT, (msg) => {
    if (msg && msg.senderId !== roomStore.self?.id) {
      toastStore.push({
        type: 'info', icon: '💬', durationMs: 2200,
        title: msg.sender || '新消息',
        body: msg.content
      })
    }
  })

  // Bug 11：非文件页有上传 → toast
  unsubFile = stomp.subscribe(TOPIC.FILE_PROGRESS, (ev) => {
    if (!ev) return
    if (isOnPath('/files')) return
    if (ev.stage === 'UPLOADED') {
      toastStore.push({
        type: 'ok', icon: '📁', durationMs: 2500,
        title: '新文件',
        body: ev.name || ''
      })
    }
  })
})

// 一旦 STOMP 连上 + 已加入 → 发 player.online 帧绑定 session（Bug 7）
watch(
  () => [stomp.connected.value, roomStore.self?.id],
  ([connected, pid]) => {
    if (connected && pid) {
      stomp.publish(APP.PLAYER_ONLINE, { playerId: pid })
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (unsubChat) unsubChat()
  if (unsubPlayers) unsubPlayers()
  if (unsubFile) unsubFile()
})
</script>

<style>
*, *::before, *::after { box-sizing: border-box; }
html, body, #app { height: 100%; }
body { margin: 0; font-family: 'Segoe UI', Tahoma, sans-serif; background: #f5f7fa; color: #1f2937; }
#app { display: flex; flex-direction: column; }

.app-bar {
  display: flex; align-items: center; gap: 1.5rem;
  padding: 0.75rem 1.5rem; background: white;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.brand { font-weight: 700; font-size: 1.1rem; color: #16a34a; }
.brand .ver { font-size: 0.75rem; color: #6b7280; margin-left: .25rem; }
.nav { display: flex; gap: 1rem; flex: 1; }
.nav a { color: #374151; text-decoration: none; padding: .25rem .5rem; border-radius: 4px; }
.nav a.router-link-active.router-link-exact-active { background: #dcfce7; color: #15803d; }
.status-pill {
  display: inline-flex; align-items: center; gap: .4rem;
  font-size: .85rem; color: #6b7280;
  padding: .35rem .7rem; background: #f3f4f6; border-radius: 999px;
}
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot.on { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,.2); }
.dot.off { background: #ef4444; }

.app-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  padding: 1rem;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}
.app-body.joined {
  grid-template-columns: 320px 1fr 280px;
}
.left-col, .right-col { min-height: 0; }
.center-col {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1.25rem;
  overflow-y: auto;
  min-height: 0;
}
button { cursor: pointer; }
@media (max-width: 1100px) {
  .app-body.joined { grid-template-columns: 280px 1fr; }
  .right-col { display: none; }
}
@media (max-width: 800px) {
  .app-body.joined { grid-template-columns: 1fr; grid-auto-rows: minmax(280px, auto); }
  .left-col { order: 2; }
}
</style>
