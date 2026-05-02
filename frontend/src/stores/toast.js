import { defineStore } from 'pinia'
import { ref } from 'vue'

let _id = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  function push({ type = 'info', icon = '', title, body, durationMs = 3500 }) {
    const id = ++_id
    toasts.value.push({ id, type, icon, title, body })
    if (durationMs > 0) {
      setTimeout(() => dismiss(id), durationMs)
    }
    return id
  }

  function dismiss(id) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  function clear() { toasts.value = [] }

  return { toasts, push, dismiss, clear }
})
