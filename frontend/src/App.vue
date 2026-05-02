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
        {{ stomp.connected.value ? '在线' : (reconnectMsg || '离线') }}
        · {{ appStore.isHost ? 'HOST' : 'CUSTOMER' }}
        · {{ appStore.selfNodeId || '—' }}
        <span v-if="appStore.selfHostname"> ({{ appStore.selfHostname }})</span>
      </div>
    </header>

    <div v-if="!stomp.connected.value && appStore.initialized" class="reconnect-banner">
      与 Host 的连接已断开，正在重新连接（第 {{ stomp.reconnectAttempt.value }} 次尝试）…
    </div>

    <div :class="['app-body', { joined: roomStore.hasJoined }]">
      <ChatPanel v-if="roomStore.hasJoined" class="left-col" />
      <main class="center-col">
        <router-view />
      </main>
      <SettingsPanel v-if="roomStore.hasJoined" class="right-col" />
    </div>

    <ToastHost />
    <InvitationDialog />
    <PrivateChatDock v-if="roomStore.hasJoined" />
    <UserDetailPanel />
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoomStore } from './stores/room'
import { useAppStore } from './stores/app'
import { useStomp } from './composables/useStomp'
import { useWebSocket } from './composables/useWebSocket'
import { APP } from './appConfig'
import { useRouter } from 'vue-router'

import ChatPanel from './components/ChatPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import ToastHost from './components/ToastHost.vue'
import InvitationDialog from './components/InvitationDialog.vue'
import PrivateChatDock from './components/PrivateChatDock.vue'
import UserDetailPanel from './components/UserDetailPanel.vue'

const roomStore = useRoomStore()
const appStore = useAppStore()
const stomp = useStomp()
const ws = useWebSocket()
const router = useRouter()

const reconnectMsg = computed(() =>
  stomp.reconnectAttempt.value > 0 ? `重连中…` : ''
)

onMounted(async () => {
  ws.init()
  // 启动时尝试同 IP 复用账号
  await roomStore.bootstrap(appStore.selfHostname)
  // 一旦 STOMP 连上 + 已加入 → 发 player.online 帧；
  // 同时 useWebSocket.onConnect 也会触发，无需重复
})

watch(
  () => [stomp.connected.value, roomStore.self?.id],
  ([connected, pid]) => {
    if (connected && pid) {
      stomp.publish(APP.PLAYER_ONLINE, { playerId: pid })
    }
  },
  { immediate: true }
)

// 游戏开始通知 → 仅参与者跳转到 /game
watch(() => roomStore.gameStartInfo, (info) => {
  if (!info) return
  const myId = roomStore.self?.id
  if (info.players && info.players.includes(myId)) {
    if (router.currentRoute.value.path !== '/game') {
      router.push('/game')
    }
  }
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

.reconnect-banner {
  padding: .55rem 1rem; background: #fef3c7; color: #92400e;
  border-bottom: 1px solid #fde68a; font-size: .85rem; text-align: center;
}

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
