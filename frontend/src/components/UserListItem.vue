<template>
  <div :class="['user-item', `status-${legacyStatus}`, { me, clickable }]"
       @click="onClick"
       :title="tooltip">
    <Avatar :player="user" :size="size" />
    <div class="info">
      <div class="name">
        {{ user.name }}<span v-if="me" class="me-tag">我</span>
      </div>
      <div class="meta">{{ user.ip || '?' }}<span v-if="user.hostname"> · {{ user.hostname }}</span></div>
    </div>
    <div class="user-status-dots">
      <span class="status-dot" :class="user.backendAlive ? 'dot-green' : 'dot-gray'"
            title="后端进程存活（UDP 心跳）" />
      <span class="status-dot" :class="user.wsAlive ? 'dot-green' : 'dot-gray'"
            title="前端 WebSocket 连接存活" />
      <span class="status-dot" :class="user.pageActive ? 'dot-green' : 'dot-gray'"
            title="标签页处于激活状态" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Avatar from './Avatar.vue'

const props = defineProps({
  user: { type: Object, required: true },
  me: { type: Boolean, default: false },
  size: { type: Number, default: 28 },
  clickable: { type: Boolean, default: true }
})
const emit = defineEmits(['click'])

const legacyStatus = computed(() => (props.user.status || 'OFFLINE').toLowerCase())

const tooltip = computed(() => {
  const labels = []
  labels.push(`${props.user.name}`)
  labels.push(props.user.backendAlive ? '后端在线' : '后端离线')
  labels.push(props.user.wsAlive ? 'WS 已连' : 'WS 断开')
  labels.push(props.user.pageActive ? '标签页激活' : '标签页未激活')
  return labels.join(' · ')
})

function onClick() {
  if (props.clickable) emit('click', props.user)
}
</script>

<style scoped>
.user-item {
  display: inline-flex; align-items: center; gap: .4rem;
  padding: .25rem .55rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  font-size: .8rem;
  position: relative;
  max-width: 100%;
  user-select: none;
}
.user-item.clickable { cursor: pointer; }
.user-item.clickable:hover { background: #f3f4f6; border-color: #d1d5db; }
.user-item.me { background: #dcfce7; border-color: #86efac; }
.info { display: flex; flex-direction: column; line-height: 1.1; min-width: 0; }
.name { font-weight: 500; max-width: 110px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.me-tag { color: #15803d; margin-left: .3rem; font-size: .65rem; }
.meta { color: #9ca3af; font-size: .65rem; max-width: 110px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.user-status-dots {
  display: inline-flex; gap: 3px; align-items: center; flex-shrink: 0;
  padding-left: .15rem;
}
.status-dot {
  width: 7px; height: 7px; border-radius: 50%;
  flex-shrink: 0;
}
.dot-green { background-color: #22c55e; box-shadow: 0 0 0 1.5px rgba(34,197,94,.2); }
.dot-gray  { background-color: #d1d5db; }

.user-item.status-page_closed .name { color: #6b7280; }
.user-item.status-offline .name { color: #9ca3af; text-decoration: line-through; }
</style>
