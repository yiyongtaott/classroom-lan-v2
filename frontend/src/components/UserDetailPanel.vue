<template>
  <div v-if="user" class="overlay" @click.self="close">
    <div class="panel">
      <header>
        <h3>{{ isMe ? '我的资料' : '用户资料' }}</h3>
        <button class="close-btn" @click="close">×</button>
      </header>

      <div v-if="isMe" class="content">
        <SettingsPanelInline />
      </div>

      <div v-else class="content">
        <div class="profile">
          <Avatar :player="user" :size="96" />
          <div class="title">{{ user.name }}</div>
          <div class="subtitle">{{ user.ip }}<span v-if="user.hostname"> · {{ user.hostname }}</span></div>
        </div>

        <dl class="info">
          <dt>状态</dt>
          <dd>
            <div class="dots">
              <span class="dot" :class="user.backendAlive ? 'green' : 'gray'" />
              <span class="dot" :class="user.wsAlive ? 'green' : 'gray'" />
              <span class="dot" :class="user.pageActive ? 'green' : 'gray'" />
              <span class="status-text">{{ statusText }}</span>
            </div>
          </dd>

          <dt>玩家 ID</dt><dd>{{ shortId(user.id) }}</dd>
          <dt>本场得分</dt><dd>{{ score }}</dd>
        </dl>

        <div class="actions">
          <button class="btn primary" @click="invite">私聊</button>
          <button class="btn" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserDetailStore } from '../stores/userDetail'
import { useUserListStore } from '../stores/userList'
import { useRoomStore } from '../stores/room'
import { useStomp } from '../composables/useStomp'
import { usePrivateChatStore } from '../stores/privateChat'
import { APP } from '../appConfig'
import Avatar from './Avatar.vue'
import SettingsPanelInline from './SettingsPanel.vue'

const detail = useUserDetailStore()
const userList = useUserListStore()
const roomStore = useRoomStore()
const stomp = useStomp()
const privateChat = usePrivateChatStore()

const user = computed(() => detail.targetId ? userList.find(detail.targetId) : null)
const isMe = computed(() => roomStore.self?.id === detail.targetId)

const statusText = computed(() => {
  const u = user.value
  if (!u) return '—'
  if (!u.backendAlive) return '后端已下线'
  if (!u.wsAlive) return '页面已关闭'
  if (!u.pageActive) return '标签页未激活'
  return '在线'
})

const score = computed(() => {
  const last = roomStore.lastGameState
  if (!last || !last.data || !last.data.scores) return 0
  return last.data.scores[user.value?.id] || 0
})

function close() {
  detail.close()
  // 关闭详查 → 同时清理私聊会话（业务文档要求）
  if (user.value) privateChat.closeSession(user.value.id)
}

function invite() {
  if (!user.value || isMe.value) return
  // 直接打开会话窗口；先发邀请通知（对方可决定是否打开）
  privateChat.openSession(user.value.id)
  stomp.publish(APP.PRIVATE_INVITE, { receiverId: user.value.id })
}

function shortId(id) { return id ? (id.length > 8 ? id.slice(0, 8) : id) : '—' }
</script>

<style scoped>
.overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 9100;
  display: flex; align-items: center; justify-content: center;
}
.panel {
  background: white; border-radius: 10px;
  width: 420px; max-width: 90%; max-height: 80vh; overflow: hidden;
  display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,.25);
}
header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 1rem 1.25rem; border-bottom: 1px solid #f3f4f6;
}
header h3 { margin: 0; color: #16a34a; }
.close-btn {
  background: transparent; border: none; cursor: pointer;
  font-size: 1.5rem; color: #6b7280;
}
.close-btn:hover { color: #1f2937; }
.content { padding: 1.25rem; overflow-y: auto; }

.profile { text-align: center; }
.profile .title { font-size: 1.2rem; font-weight: 700; margin-top: .5rem; }
.profile .subtitle { color: #6b7280; font-size: .85rem; }

.info { display: grid; grid-template-columns: auto 1fr; gap: .5rem 1rem; margin: 1.25rem 0; }
.info dt { color: #6b7280; font-size: .85rem; }
.info dd { margin: 0; font-family: ui-monospace, monospace; font-size: .9rem; }

.dots { display: inline-flex; align-items: center; gap: .35rem; }
.dot { width: 9px; height: 9px; border-radius: 50%; }
.dot.green { background: #22c55e; }
.dot.gray { background: #d1d5db; }
.status-text { font-family: 'Segoe UI', sans-serif; color: #1f2937; font-size: .85rem; margin-left: .35rem; }

.actions { display: flex; gap: .5rem; margin-top: 1rem; }
.btn { flex: 1; padding: .55rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
</style>
