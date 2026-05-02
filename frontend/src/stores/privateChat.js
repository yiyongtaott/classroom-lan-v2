import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * FEATURE-04 私聊会话状态。
 * 不持久化，仅当前会话内存。当 UserDetailPanel 关闭时调用 closeSession 清空。
 *
 * sessions: { [peerId]: { messages: [], minimized: false, open: false, unread: 0, peer: {...} } }
 */
export const usePrivateChatStore = defineStore('privateChat', () => {
  const sessions = ref({})
  const pendingInvites = ref([]) // [{senderId, senderName, ts}]

  function ensure(peerId) {
    if (!sessions.value[peerId]) {
      sessions.value[peerId] = { messages: [], minimized: false, open: false, unread: 0, peerId }
    }
    return sessions.value[peerId]
  }

  function openSession(peerId) {
    const s = ensure(peerId)
    s.open = true
    s.minimized = false
    s.unread = 0
  }

  function minimizeSession(peerId) {
    const s = ensure(peerId)
    s.minimized = true
  }

  function closeSession(peerId) {
    delete sessions.value[peerId]
  }

  function addMessage(peerId, msg) {
    const s = ensure(peerId)
    s.messages.push(msg)
    if (s.messages.length > 200) s.messages.shift()
    if (!s.open || s.minimized) s.unread++
  }

  function handleIncomingInvite(msg) {
    pendingInvites.value.push(msg)
  }

  function dismissInvite(senderId) {
    pendingInvites.value = pendingInvites.value.filter(m => m.senderId !== senderId)
  }

  function handleInviteResponse(msg) {
    // 没有特殊处理 - 由 PrivateChatWindow 监听 toast 提示
  }

  return {
    sessions, pendingInvites,
    openSession, minimizeSession, closeSession, addMessage,
    handleIncomingInvite, dismissInvite, handleInviteResponse
  }
})
