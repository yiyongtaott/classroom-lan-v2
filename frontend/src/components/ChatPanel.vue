<template>
  <aside class="chat-panel">
    <header>
      <h3>聊天</h3>
      <span class="count">{{ players.length }} 在线</span>
    </header>

    <div class="players-row">
      <PlayerChip v-for="p in players" :key="p.id" :player="p" :me="roomStore.self?.id === p.id" />
    </div>

    <div class="messages" ref="msgBox">
      <div v-for="(m, i) in messages" :key="i"
           :class="['msg', { mine: m.senderId === roomStore.self?.id }]">
        <Avatar :player="senderOf(m)" :size="28" />
        <div class="bubble">
          <div class="meta">
            <strong>{{ m.sender }}</strong>
            <small>{{ shortTime(m.timestamp) }}</small>
          </div>
          <div class="body">{{ m.content }}</div>
        </div>
      </div>
      <div v-if="messages.length === 0" class="empty">暂无消息</div>
    </div>

    <form class="input" @submit.prevent="send">
      <input v-model="text"
             placeholder="输入消息回车发送"
             :disabled="!stomp.connected.value || !roomStore.hasJoined" />
      <button class="btn primary" :disabled="!stomp.connected.value || !text.trim() || !roomStore.hasJoined">发送</button>
    </form>
  </aside>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoomStore } from '../stores/room'
import { useUserListStore } from '../stores/userList'
import { useStomp } from '../composables/useStomp'
import { APP } from '../appConfig'
import Avatar from './Avatar.vue'
import PlayerChip from './PlayerChip.vue'

const roomStore = useRoomStore()
const userList = useUserListStore()
const stomp = useStomp()

const text = ref('')
const msgBox = ref(null)

const players = computed(() => userList.users)
const messages = computed(() => roomStore.messages)

function senderOf(msg) {
  if (!msg) return null
  if (msg.senderId) {
    const p = userList.find(msg.senderId)
    if (p) return p
  }
  return userList.users.find(x => x.name === msg.sender) || { name: msg.sender }
}

function shortTime(ts) {
  if (!ts) return ''
  const i = ts.indexOf('T')
  return i > 0 ? ts.slice(i + 1, i + 9) : ts
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

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

watch(messages, () => scrollToBottom(), { deep: true, flush: 'post' })
onMounted(() => scrollToBottom())
</script>

<style scoped>
.chat-panel {
  display: flex; flex-direction: column;
  height: 100%;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  min-height: 0;
}
header {
  display: flex; align-items: baseline; justify-content: space-between;
  padding: .85rem 1rem; border-bottom: 1px solid #f3f4f6; flex-shrink: 0;
}
header h3 { margin: 0; color: #16a34a; font-size: 1rem; }
.count { color: #6b7280; font-size: .8rem; }

.players-row {
  display: flex; gap: .35rem; flex-wrap: wrap;
  padding: .5rem .75rem; border-bottom: 1px solid #f3f4f6; flex-shrink: 0;
  max-height: 110px; overflow-y: auto;
}

.messages {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: .75rem;
}
.msg { display: flex; gap: .5rem; padding: .4rem 0; border-bottom: 1px solid #f9fafb; }
.msg.mine .meta strong { color: #16a34a; }
.bubble { flex: 1; min-width: 0; }
.meta { font-size: .75rem; color: #6b7280; margin-bottom: .15rem; }
.body { word-break: break-word; }
.empty { color: #9ca3af; padding: 2rem 1rem; text-align: center; font-size: .9rem; }

.input {
  display: flex; gap: .5rem;
  padding: .6rem;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.input input {
  flex: 1; padding: .5rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: .9rem;
}
.btn { padding: .5rem .9rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
</style>
