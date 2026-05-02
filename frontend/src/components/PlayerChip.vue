<template>
  <div :class="['player-chip', `status-${status}`, { me }]" :title="tooltip">
    <Avatar :player="player" :size="24" />
    <div class="info">
      <div class="name">{{ player.name }}<span v-if="me" class="me-tag">我</span></div>
      <div class="meta">{{ player.ip || '?' }}<span v-if="player.hostname"> · {{ player.hostname }}</span></div>
    </div>
    <span :class="['dot', status]" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Avatar from './Avatar.vue'

const props = defineProps({
  player: { type: Object, required: true },
  me: { type: Boolean, default: false }
})

const status = computed(() => (props.player.status || 'OFFLINE').toLowerCase())

const tooltip = computed(() => {
  const map = { online: '在线', page_closed: '页面关闭', offline: '离线' }
  return `${props.player.name} · ${map[status.value] || ''}`
})
</script>

<style scoped>
.player-chip {
  display: inline-flex; align-items: center; gap: .4rem;
  padding: .25rem .55rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  font-size: .8rem;
  position: relative;
  max-width: 100%;
}
.player-chip.me { background: #dcfce7; border-color: #86efac; }
.info { display: flex; flex-direction: column; line-height: 1.1; min-width: 0; }
.name { font-weight: 500; max-width: 110px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.me-tag { color: #15803d; margin-left: .3rem; font-size: .65rem; }
.meta { color: #9ca3af; font-size: .65rem; max-width: 110px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.online { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,.2); }
.dot.page_closed { background: #f59e0b; }
.dot.offline { background: #d1d5db; }

.player-chip.status-page_closed .name { color: #6b7280; }
.player-chip.status-offline .name { color: #9ca3af; text-decoration: line-through; }
</style>
