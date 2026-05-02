import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * FEATURE-02：用户详查面板状态。
 * 一次只展示一个用户。点击自己 → 编辑模式（复用 SettingsPanel 内的能力）。
 */
export const useUserDetailStore = defineStore('userDetail', () => {
  const targetId = ref(null)

  function open(userId) { targetId.value = userId }
  function close() { targetId.value = null }
  function isOpenFor(id) { return targetId.value === id }

  return { targetId, open, close, isOpenFor }
})
