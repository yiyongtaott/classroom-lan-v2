<template>
  <section class="home">
    <h1>ClassroomLAN v2</h1>
    <p class="tagline">局域网内自动选主的实时娱乐系统</p>

    <div class="grid">
      <div class="card">
        <div class="card-title">本机角色（你的视角）</div>
        <div :class="['role', roomStore.isHost ? 'host' : 'client']">
          {{ roomStore.isHost ? 'HOST（本机即后端）' : 'CUSTOMER（普通客户端）' }}
        </div>
        <dl>
          <dt>本机 IP</dt><dd>{{ roomStore.nodeId || '—' }}</dd>
          <dt>系统名</dt>
          <dd>{{ roomStore.hostname || (roomStore.isHost ? '—' : '（仅 Host 可知）') }}</dd>
        </dl>
      </div>

      <div class="card">
        <div class="card-title">当前 Host</div>
        <div class="role host" v-if="roomStore.hostNodeId">
          {{ roomStore.hostHostname || 'HOST' }}
        </div>
        <dl>
          <dt>Host IP</dt><dd>{{ roomStore.hostNodeId || '—' }}</dd>
          <dt>已发现节点</dt><dd>{{ roomStore.peerCount }}</dd>
          <dt>玩家数</dt><dd>{{ roomStore.players.length }}</dd>
          <dt>当前游戏</dt><dd>{{ roomStore.gameType || '空闲' }}</dd>
        </dl>
        <div class="actions">
          <router-link v-if="!roomStore.hasJoined" to="/join" class="btn primary">加入房间</router-link>
          <router-link v-else to="/chat" class="btn primary">进入聊天</router-link>
          <button @click="refresh" class="btn">刷新</button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoomStore } from '../stores/room'

const roomStore = useRoomStore()

async function refresh() {
  await Promise.all([roomStore.refreshStatus(), roomStore.refreshSnapshot()])
}

onMounted(refresh)
</script>

<style scoped>
.home { max-width: 900px; margin: 0 auto; }
h1 { color: #16a34a; margin-bottom: .25rem; }
.tagline { color: #6b7280; margin-top: 0; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 2rem; }
.card { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 1.25rem; }
.card-title { font-weight: 600; color: #6b7280; font-size: .8rem; text-transform: uppercase; letter-spacing: .04em; margin-bottom: .75rem; }
.role { font-size: 1.25rem; font-weight: 700; padding: .5rem .8rem; border-radius: 6px; display: inline-block; margin-bottom: .75rem; }
.role.host { background: #dcfce7; color: #15803d; }
.role.client { background: #dbeafe; color: #1d4ed8; }
dl { margin: 0; display: grid; grid-template-columns: auto 1fr; gap: .25rem .75rem; }
dt { color: #6b7280; font-size: .85rem; }
dd { margin: 0; font-family: ui-monospace, monospace; word-break: break-all; }
.actions { margin-top: 1rem; display: flex; gap: .5rem; }
.btn { padding: .5rem 1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; text-decoration: none; color: #1f2937; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:hover { background: #15803d; }
@media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
</style>
