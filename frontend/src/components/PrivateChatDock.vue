<template>
  <!-- 私聊邀请通知 -->
  <div v-if="pendingInvite" class="invite-modal-backdrop" @click.self="dismiss">
    <div class="invite-modal">
      <h3>📨 私聊邀请</h3>
      <p>来自 <strong>{{ pendingInvite.senderName || pendingInvite.senderId }}</strong></p>
      <div class="actions">
        <button class="btn primary" @click="acceptInvite">打开</button>
        <button class="btn" @click="minimizeInvite">最小化</button>
        <button class="btn danger" @click="destroyInvite">拒绝</button>
      </div>
    </div>
  </div>

  <!-- 私聊会话窗口（多个并排） -->
  <div class="dock">
    <div v-for="session in openSessions" :key="session.peerId" class="window" :class="{ minimized: session.minimized }">
      <header @click="toggleMinimize(session.peerId)">
        <Avatar :player="peerOf(session.peerId)" :size="20" />
        <span class="title">{{ peerOf(session.peerId)?.name || '?' }}</span>
        <span v-if="session.unread" class="badge">{{ session.unread }}</span>
        <button class="close" @click.stop="closeSession(session.peerId)">×</button>
      </header>
      <div v-if="!session.minimized" class="body">
        <div class="messages" :ref="el => bindMsgBox(session.peerId, el)">
          <div v-for="(m, i) in session.messages" :key="i"
               :class="['msg', { mine: m.senderId === me?.id }]">
            <div class="bubble">{{ m.content }}</div>
            <div class="ts">{{ tsLabel(m.ts) }}</div>
          </div>
          <div v-if="session.messages.length === 0" class="empty">说点什么吧…</div>
        </div>
        <form class="input" @submit.prevent="send(session.peerId)">
          <input v-model="drafts[session.peerId]" placeholder="输入消息" />
          <button class="btn" type="submit">发送</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, reactive, watch, nextTick } from 'vue'
import { usePrivateChatStore } from '../stores/privateChat'
import { useRoomStore } from '../stores/room'
import { useUserListStore } from '../stores/userList'
import { useStomp } from '../composables/useStomp'
import { APP } from '../appConfig'
import Avatar from './Avatar.vue'

const privateChat = usePrivateChatStore()
const roomStore = useRoomStore()
const userList = useUserListStore()
const stomp = useStomp()

const drafts = reactive({})
const msgBoxes = new Map()

const me = computed(() => roomStore.self)
const openSessions = computed(() =>
  Object.values(privateChat.sessions).filter(s => s.open)
)
const pendingInvite = computed(() =>
  privateChat.pendingInvites.length ? privateChat.pendingInvites[0] : null
)

function peerOf(peerId) {
  return userList.find(peerId) || { name: peerId, id: peerId }
}

function tsLabel(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('zh-CN', { hour12: false })
}

function bindMsgBox(peerId, el) {
  if (el) msgBoxes.set(peerId, el)
}

function toggleMinimize(peerId) {
  const s = privateChat.sessions[peerId]
  if (!s) return
  s.minimized = !s.minimized
  if (!s.minimized) s.unread = 0
}

function closeSession(peerId) {
  privateChat.closeSession(peerId)
}

function send(peerId) {
  const text = (drafts[peerId] || '').trim()
  if (!text) return
  stomp.publish(APP.PRIVATE_CHAT_SEND, { receiverId: peerId, content: text })
  drafts[peerId] = ''
}

function acceptInvite() {
  if (!pendingInvite.value) return
  const { senderId } = pendingInvite.value
  privateChat.openSession(senderId)
  stomp.publish(APP.PRIVATE_INVITE_RESPOND, { targetId: senderId, response: 'OPEN' })
  privateChat.dismissInvite(senderId)
}

function minimizeInvite() {
  if (!pendingInvite.value) return
  const { senderId } = pendingInvite.value
  privateChat.openSession(senderId)
  privateChat.minimizeSession(senderId)
  stomp.publish(APP.PRIVATE_INVITE_RESPOND, { targetId: senderId, response: 'MINIMIZE' })
  privateChat.dismissInvite(senderId)
}

function destroyInvite() {
  if (!pendingInvite.value) return
  const { senderId } = pendingInvite.value
  stomp.publish(APP.PRIVATE_INVITE_RESPOND, { targetId: senderId, response: 'DESTROY' })
  privateChat.dismissInvite(senderId)
}

function dismiss() {
  destroyInvite()
}

watch(() => openSessions.value.map(s => s.messages.length).join(','), () => {
  nextTick(() => {
    for (const [peerId, el] of msgBoxes.entries()) {
      el.scrollTop = el.scrollHeight
    }
  })
})
</script>

<style scoped>
.invite-modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 9200;
  display: flex; align-items: center; justify-content: center;
}
.invite-modal {
  background: white; border-radius: 10px; padding: 1.25rem 1.5rem;
  max-width: 360px; width: 90%;
  box-shadow: 0 20px 60px rgba(0,0,0,.25);
}
.invite-modal h3 { margin: 0 0 .5rem; color: #16a34a; }
.invite-modal .actions { display: flex; gap: .5rem; margin-top: 1rem; }
.btn { flex: 1; padding: .55rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }

.dock {
  position: fixed; bottom: 0; right: 1rem; z-index: 8500;
  display: flex; gap: .5rem; align-items: flex-end;
}
.window {
  width: 280px; background: white;
  border: 1px solid #e5e7eb;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  box-shadow: 0 6px 20px rgba(0,0,0,.12);
  display: flex; flex-direction: column;
  max-height: 360px;
}
.window.minimized { max-height: 36px; }
.window header {
  display: flex; align-items: center; gap: .5rem;
  padding: .45rem .65rem;
  background: #16a34a;
  color: white;
  cursor: pointer;
  border-radius: 8px 8px 0 0;
}
.window header .title { flex: 1; font-size: .85rem; font-weight: 600; }
.window header .badge {
  background: #fef3c7; color: #92400e;
  padding: .05rem .4rem; border-radius: 999px; font-size: .65rem;
}
.window header .close {
  background: transparent; border: none; color: white; cursor: pointer;
  font-size: 1.1rem; padding: 0 .25rem;
}
.window .body {
  display: flex; flex-direction: column;
  flex: 1; min-height: 0;
}
.window .messages {
  flex: 1; overflow-y: auto; padding: .5rem; min-height: 100px;
  display: flex; flex-direction: column; gap: .35rem;
}
.window .msg { display: flex; flex-direction: column; }
.window .msg.mine { align-items: flex-end; }
.window .msg .bubble {
  background: #f3f4f6; padding: .35rem .55rem; border-radius: 8px; font-size: .85rem;
  max-width: 75%; word-break: break-word;
}
.window .msg.mine .bubble { background: #dcfce7; color: #15803d; }
.window .msg .ts { font-size: .65rem; color: #9ca3af; margin-top: .15rem; }
.window .empty { color: #9ca3af; text-align: center; font-size: .8rem; padding: 1rem; }
.window .input {
  display: flex; gap: .35rem; padding: .4rem .5rem;
  border-top: 1px solid #f3f4f6;
}
.window .input input {
  flex: 1; padding: .35rem; border: 1px solid #d1d5db; border-radius: 4px; font-size: .85rem;
}
.window .input .btn {
  padding: .25rem .65rem; background: #16a34a; color: white;
  border: 1px solid #16a34a; border-radius: 4px; font-size: .8rem;
  flex: 0 0 auto;
}
</style>
