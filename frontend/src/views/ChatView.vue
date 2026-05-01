<template>
  <section class="chat">
    <header class="ch-head">
      <h2>聊天</h2>
      <span class="me">我: <strong>{{ roomStore.self?.name || '未加入' }}</strong></span>
    </header>

    <div class="layout">
      <aside class="players">
        <div class="aside-title">在线玩家 ({{ roomStore.players.length }})</div>
        <ul>
          <li v-for="p in roomStore.players" :key="p.id" :class="{ self: p.id === roomStore.self?.id }">
            <Avatar :player="p" :size="32" />
            <div class="player-info">
              <div class="player-name">
                {{ p.name }}<span v-if="p.id === roomStore.self?.id" class="tag">我</span>
              </div>
              <div class="player-ip" v-if="p.ip">{{ p.ip }}</div>
            </div>
          </li>
        </ul>
      </aside>

      <div class="main">
        <div class="messages" ref="msgBox">
          <div v-for="(m, i) in roomStore.messages" :key="i" class="msg" :class="{ mine: m.senderId === roomStore.self?.id }">
            <Avatar :player="senderOf(m)" :size="32" />
            <div class="bubble">
              <div class="meta"><strong>{{ m.sender }}</strong> <small>{{ shortTime(m.timestamp) }}</small></div>
              <div class="body">{{ m.content }}</div>
            </div>
          </div>
          <div v-if="roomStore.messages.length === 0" class="empty">暂无消息</div>
        </div>
        <form class="input" @submit.prevent="send">
          <input v-model="text" placeholder="输入消息，回车发送" :disabled="!stomp.connected.value || !roomStore.hasJoined" />
          <button class="btn primary" :disabled="!stomp.connected.value || !text.trim() || !roomStore.hasJoined">发送</button>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import { useRoomStore } from '../stores/room'
import { useStomp } from '../composables/useStomp'
import { TOPIC, APP } from '../appConfig'
import Avatar from '../components/Avatar.vue'

const roomStore = useRoomStore()
const stomp = useStomp()

const text = ref('')
const msgBox = ref(null)
let unsub = null
let snapshotTimer = null

function send() {
  const content = text.value.trim()
  if (!content || !roomStore.hasJoined) return
  stomp.publish(APP.CHAT_SEND, {
    sender: roomStore.self.name,
    senderId: roomStore.self.id,
    content
  })
  text.value = ''
}

function senderOf(msg) {
  if (!msg) return null
  if (msg.senderId) {
    const p = roomStore.players.find(x => x.id === msg.senderId)
    if (p) return p
  }
  // fallback：用 sender 名字匹配
  return roomStore.players.find(x => x.name === msg.sender) || { name: msg.sender }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

function shortTime(ts) {
  if (!ts) return ''
  const idx = ts.indexOf('T')
  return idx > 0 ? ts.slice(idx + 1, idx + 9) : ts
}

onMounted(async () => {
  await Promise.all([
    roomStore.refreshSnapshot(),
    roomStore.loadChatHistory()
  ])
  scrollToBottom()
  unsub = stomp.subscribe(TOPIC.CHAT, (msg) => {
    roomStore.appendMessage(msg)
    scrollToBottom()
  })
  snapshotTimer = setInterval(() => roomStore.refreshSnapshot().catch(() => {}), 5000)
})

onBeforeUnmount(() => {
  if (unsub) unsub()
  if (snapshotTimer) clearInterval(snapshotTimer)
})
</script>

<style scoped>
.chat { max-width: 1000px; margin: 0 auto; }
.ch-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 1rem; }
.ch-head h2 { margin: 0; color: #16a34a; }
.me { color: #6b7280; font-size: .9rem; }
.layout { display: grid; grid-template-columns: 240px 1fr; gap: 1rem; height: 65vh; }
.players { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 1rem; overflow-y: auto; }
.aside-title { color: #6b7280; font-size: .8rem; text-transform: uppercase; margin-bottom: .5rem; }
.players ul { list-style: none; padding: 0; margin: 0; }
.players li { display: flex; gap: .6rem; align-items: center; padding: .5rem 0; border-bottom: 1px dashed #f3f4f6; }
.player-info { flex: 1; min-width: 0; }
.player-name { font-weight: 500; }
.players li.self .player-name { color: #15803d; }
.player-ip { color: #9ca3af; font-size: .75rem; font-family: ui-monospace, monospace; }
.tag { background: #dcfce7; color: #15803d; font-size: .7rem; padding: .1rem .4rem; border-radius: 4px; margin-left: .35rem; }
.main { display: flex; flex-direction: column; background: white; border: 1px solid #e5e7eb; border-radius: 8px; }
.messages { flex: 1; overflow-y: auto; padding: 1rem; }
.msg { display: flex; gap: .6rem; padding: .5rem 0; border-bottom: 1px solid #f3f4f6; }
.bubble { flex: 1; min-width: 0; }
.msg.mine .meta strong { color: #16a34a; }
.msg .meta { font-size: .8rem; color: #6b7280; margin-bottom: .15rem; }
.msg .body { word-wrap: break-word; word-break: break-all; }
.empty { color: #9ca3af; padding: 2rem; text-align: center; }
.input { display: flex; gap: .5rem; padding: .75rem; border-top: 1px solid #e5e7eb; }
.input input { flex: 1; padding: .55rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; }
.btn { padding: .55rem 1.1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
@media (max-width: 720px) { .layout { grid-template-columns: 1fr; height: auto; } }
</style>
