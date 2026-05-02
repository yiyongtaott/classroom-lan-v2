<template>
  <div class="toasts">
    <transition-group name="toast">
      <div v-for="t in store.toasts" :key="t.id" :class="['toast', t.type]" @click="store.dismiss(t.id)">
        <span class="icon" v-if="t.icon">{{ t.icon }}</span>
        <div class="text">
          <div class="title" v-if="t.title">{{ t.title }}</div>
          <div class="body">{{ t.body }}</div>
        </div>
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
  display: flex; flex-direction: column; gap: .5rem; max-width: 320px;
}
.toast {
  display: flex; align-items: center; gap: .65rem;
  padding: .7rem .9rem;
  background: white;
  border: 1px solid #e5e7eb;
  border-left: 4px solid #16a34a;
  border-radius: 6px;
  box-shadow: 0 6px 16px rgba(0,0,0,.08);
  cursor: pointer;
  font-size: .9rem;
}
.toast.info { border-left-color: #2563eb; }
.toast.ok { border-left-color: #16a34a; }
.toast.warn { border-left-color: #f59e0b; }
.toast.err { border-left-color: #dc2626; }
.toast .icon { font-size: 1.4rem; }
.toast .title { font-weight: 600; }
.toast .body { color: #6b7280; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(20px); }
.toast-enter-active, .toast-leave-active { transition: all .25s ease; }
</style>
