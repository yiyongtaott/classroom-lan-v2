<template>
  <div id="app">
    <header class="app-bar">
      <div class="brand">📡 ClassroomLAN <span class="ver">v2</span></div>
      <nav class="nav">
        <router-link to="/">主页</router-link>
        <router-link to="/join" v-if="!roomStore.hasJoined">加入</router-link>
        <router-link to="/chat" v-if="roomStore.hasJoined">聊天</router-link>
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
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useRoomStore } from './stores/room'
import { useStomp } from './composables/useStomp'

const roomStore = useRoomStore()
const stomp = useStomp()

let pollTimer = null

onMounted(async () => {
  stomp.connect()
  try {
    await roomStore.refreshStatus()
  } catch (e) {
    console.warn('refresh status failed', e)
  }
  pollTimer = setInterval(() => roomStore.refreshStatus().catch(() => {}), 3000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style>
*, *::before, *::after { box-sizing: border-box; }
body { margin: 0; font-family: 'Segoe UI', Tahoma, sans-serif; background: #f5f7fa; color: #1f2937; }
#app { min-height: 100vh; display: flex; flex-direction: column; }
.app-bar {
  display: flex; align-items: center; gap: 1.5rem;
  padding: 0.75rem 1.5rem; background: white;
  border-bottom: 1px solid #e5e7eb;
}
.brand { font-weight: 700; font-size: 1.1rem; color: #16a34a; }
.brand .ver { font-size: 0.75rem; color: #6b7280; margin-left: .25rem; }
.nav { display: flex; gap: 1rem; flex: 1; }
.nav a { color: #374151; text-decoration: none; padding: .25rem .5rem; border-radius: 4px; }
.nav a.router-link-active { background: #dcfce7; color: #15803d; }
.status-pill {
  display: inline-flex; align-items: center; gap: .4rem;
  font-size: .85rem; color: #6b7280;
  padding: .35rem .7rem; background: #f3f4f6; border-radius: 999px;
}
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot.on { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,.2); }
.dot.off { background: #ef4444; }
.app-main { flex: 1; padding: 1.5rem; max-width: 1100px; width: 100%; margin: 0 auto; }
button { cursor: pointer; }
</style>
