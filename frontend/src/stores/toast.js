import { defineStore } from 'pinia'
import { ref } from 'vue'

let _id = 0

/**
 * 全局 Toast 通道。
 *
 * push({ type, icon, title, body, durationMs, action })
 *   type: info | ok | warn | err
 *   action: { label, url } 可点击的下载/跳转链接（FEATURE-03 文件上传通知）
 */
export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  function push({ type = 'info', icon = '', title, body, durationMs = 3500, action = null }) {
    const id = ++_id
    toasts.value.push({ id, type, icon, title, body, action })
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
