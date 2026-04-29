<template>
  <div class="chat">
    <header>
      <h2>聊天室</h2>
      <span :class="['status', connected ? 'online' : 'offline']">
        {{ connected ? '在线' : '离线' }}
      </span>
    </header>

    <div class="players-sidebar">
      <h3>在线玩家 ({{ players.length }})</h3>
      <ul>
        <li v-for="player in players" :key="player.id">
          {{ player.name }} {{ player.isHost ? '(房主)' : '' }}
        </li>
      </ul>
    </div>

    <div class="chat-main">
      <div class="messages" ref="msgContainer">
        <div v-for="(msg, idx) in messages" :key="idx" class="msg">
          <strong>{{ msg.senderName }}:</strong>
          <span>{{ msg.content }}</span>
          <small>{{ msg.timestamp }}</small>
        </div>
      </div>

      <form @submit.prevent="sendMessage" class="input-area">
        <input
          v-model="newMessage"
          placeholder="输入消息..."
          autocomplete="off"
          :disabled="!connected"
        />
        <button type="submit" :disabled="!connected || !newMessage.trim()">发送</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useStomp } from '../composables/useStomp'
import { useRoomStore } from '../stores/room'

const { client, connected, subscribe, publish, connect } = useStomp()
const roomStore = useRoomStore()

const newMessage = ref('')
const msgContainer = ref(null)
const players = computed(() => roomStore.players)
const messages = computed(() => roomStore.messages)

let chatSubscription = null

onMounted(() => {
  connect()

  // 订阅聊天频道
  chatSubscription = subscribe('/topic/chat', (msg) => {
    const data = JSON.parse(msg.body)
    roomStore.addMessage(data)
    scrollToBottom()
  })

  // TODO: 拉取历史消息（可通过 REST API）
})

onUnmounted(() => {
  if (chatSubscription) chatSubscription.unsubscribe()
})

function sendMessage() {
  if (!newMessage.value.trim()) return

  const payload = {
    sender: localStorage.getItem('nickname') || 'Anonymous',
    content: newMessage.value.trim()
  }

  publish('/app/chat', payload)
  newMessage.value = ''
}

function scrollToBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.chat {
  display: flex;
  height: calc(100vh - 100px);
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-bottom: 1rem;
}
.status {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.85rem;
}
.status.online {
  background: #42b983;
  color: white;
}
.status.offline {
  background: #f56c6c;
  color: white;
}
.players-sidebar {
  width: 200px;
  padding: 1rem;
  background: #f9f9f9;
  border-right: 1px solid #eee;
}
.players-sidebar ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.players-sidebar li {
  padding: 0.25rem 0;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 1rem;
}
.messages {
  flex: 1;
  overflow-y: auto;
  border: 1px solid #eee;
  padding: 1rem;
  margin-bottom: 1rem;
  background: #fff;
}
.msg {
  margin-bottom: 0.5rem;
  line-height: 1.4;
}
.msg strong {
  color: #333;
}
.msg small {
  margin-left: 0.5rem;
  color: #999;
  font-size: 0.75rem;
}
.input-area {
  display: flex;
  gap: 0.5rem;
}
input {
  flex: 1;
  padding: 0.6rem;
  font-size: 1rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}
button {
  padding: 0.6rem 1.2rem;
  background: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
button:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
