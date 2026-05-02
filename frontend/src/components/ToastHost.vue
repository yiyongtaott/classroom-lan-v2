<template>
  <div class="toasts">
    <transition-group name="toast">
      <div v-for="t in store.toasts" :key="t.id" :class="['toast', t.type]">
        <span class="icon" v-if="t.icon">{{ t.icon }}</span>
        <div class="text" @click="store.dismiss(t.id)">
          <div class="title" v-if="t.title">{{ t.title }}</div>
          <div class="body">{{ t.body }}</div>
        </div>
        <a v-if="t.action && t.action.url" :href="t.action.url"
           class="action" target="_blank" rel="noopener" @click.stop>
          {{ t.action.label || '打开' }}
        </a>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { useToastStore } from '../stores/toast'
const store = useToastStore()
</script>

<style scoped>
.toasts {
  position: fixed; top: 1rem; right: 1rem; z-index: 9999;
  display: flex; flex-direction: column; gap: .5rem; max-width: 360px;
}
.toast {
  display: flex; align-items: center; gap: .65rem;
  padding: .7rem .9rem;
  background: white;
  border: 1px solid #e5e7eb;
  border-left: 4px solid #16a34a;
  border-radius: 6px;
  box-shadow: 0 6px 16px rgba(0,0,0,.08);
  font-size: .9rem;
}
.toast.info { border-left-color: #2563eb; }
.toast.ok { border-left-color: #16a34a; }
.toast.warn { border-left-color: #f59e0b; }
.toast.err { border-left-color: #dc2626; }
.toast .icon { font-size: 1.4rem; }
.toast .text { flex: 1; cursor: pointer; }
.toast .title { font-weight: 600; }
.toast .body { color: #6b7280; word-break: break-all; }
.toast .action {
  background: #16a34a; color: white;
  padding: .35rem .65rem; border-radius: 4px;
  text-decoration: none; font-size: .8rem;
  flex-shrink: 0;
}
.toast .action:hover { background: #15803d; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(20px); }
.toast-enter-active, .toast-leave-active { transition: all .25s ease; }
</style>
