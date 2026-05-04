import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { API_BASE } from '../appConfig'

/**
 * 房间业务状态：自身玩家信息、聊天 / 游戏 / 邀请状态、画板笔画。
 *
 * 不再轮询 /api/status 或 /api/room；状态由 useWebSocket 推送给 store：
 *   applyRoom        ← /topic/room
 *   appendMessage    ← /topic/chat
 *   setGameState     ← /topic/game.state
 *   setInvitation    ← /topic/game.invitation
 *   setInvitationState ← /topic/game.invitation.state
 *   setGameStartInfo ← /topic/game.start
 *   appendDrawStroke ← /topic/game.draw.canvas
 */
export const useRoomStore = defineStore('room', () => {
  const self = ref(JSON.parse(localStorage.getItem('self') || 'null'))
  // 获取或生成设备唯一ID
  const deviceId = ref(localStorage.getItem('deviceId') || crypto.randomUUID())
  localStorage.setItem('deviceId', deviceId.value)

  const currentRoomId = ref(localStorage.getItem('roomId') || 'default')
  const players = ref([])
  const messages = ref([])
  const gameType = ref(null)
  const lastGameState = ref(null)
  const gameLog = ref([])
  const invitation = ref(null)
  const invitationState = ref(null)
  const gameStartInfo = ref(null)
  const drawStrokes = ref([])
  const drawPrivate = ref(null)
  const reconciling = ref(false)

  const hasJoined = computed(() => !!self.value)
  const isParticipantInActiveGame = computed(() => {
    if (!gameStartInfo.value) return false
    const list = gameStartInfo.value.players || []
    return list.includes(self.value?.id)
  })

  function setSelf(player) {
    self.value = player
    localStorage.setItem('self', JSON.stringify(player))
  }

  function setRoomId(roomId) {
    currentRoomId.value = roomId
    localStorage.setItem('roomId', roomId)
  }

  function patchSelf(patch) {
    if (!self.value) return
    Object.assign(self.value, patch)
    localStorage.setItem('self', JSON.stringify(self.value))
  }

  function clearSelf() {
    self.value = null
    localStorage.removeItem('self')
  }

  /** room 快照应用（来自 /topic/room 或 /user/queue/init.room）。 */
  function applyRoom(snap) {
    if (!snap) return
    if (Array.isArray(snap.players)) players.value = snap.players
    if ('gameType' in snap) gameType.value = snap.gameType
    if (self.value) {
      const me = players.value.find(p => p.id === self.value.id)
      if (me) {
        Object.assign(self.value, me)
        localStorage.setItem('self', JSON.stringify(self.value))
      } else if (!reconciling.value) {
        ensurePresence()
      }
    }
  }

  /** 启动时优先 GET /api/me（按 IP 同账号）。 */
  async function bootstrap(hostnameFallback) {
      const res = await fetch(`${API_BASE}/me`)
      if (res.ok) {
        const me = await res.json()
        setSelf(me)
        return me
      }
    if (self.value) {
      await ensurePresence(hostnameFallback)
    }
    return self.value
  }

  async function ensurePresence(hostnameFallback) {
    if (reconciling.value) return
    reconciling.value = true
    try {
      const old = self.value
      const res = await fetch(`${API_BASE}/room/players`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: old?.name, hostname: old?.hostname || hostnameFallback })
      })
      if (!res.ok) return
      const player = await res.json()
      setSelf(player)
    } finally {
      reconciling.value = false
    }
  }

  async function joinAs({ name, hostname }) {
    const res = await fetch(`${API_BASE}/room/${currentRoomId.value}/players`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, hostname, deviceId: deviceId.value })
    })
    if (!res.ok) throw new Error(`join failed: HTTP ${res.status}`)
    const player = await res.json()
    setSelf(player)
    return player
  }

  async function updateName(newName) {
    if (!self.value) throw new Error('not joined')
    const res = await fetch(`${API_BASE}/room/${currentRoomId.value}/players/${self.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName })
    })
    if (!res.ok) throw new Error(`update HTTP ${res.status}`)
    const updated = await res.json()
    setSelf(updated)
    return updated
  }

  async function uploadAvatar(file) {
    if (!self.value) throw new Error('not joined')
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(`${API_BASE}/avatars/${self.value.id}`, { method: 'POST', body: fd })
    if (!res.ok) throw new Error(`avatar upload failed: HTTP ${res.status}`)
    const data = await res.json()
    self.value.avatar = data.avatar
    setSelf(self.value)
    return data.avatar
  }

  async function clearAvatar() {
    if (!self.value) return
    const res = await fetch(`${API_BASE}/room/players/${self.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ avatar: '' })
    })
    if (res.ok) {
      const updated = await res.json()
      setSelf(updated)
    }
  }

  async function leave() {
    if (!self.value) return
    try { await fetch(`${API_BASE}/room/${currentRoomId.value}/players/${self.value.id}`, { method: 'DELETE' }) } catch {}
    clearSelf()
    players.value = []
    messages.value = []
  }

  function appendMessage(msg) {
    messages.value.push(msg)
    if (messages.value.length > 500) messages.value.shift()
  }

  function setMessages(list) {
    messages.value = Array.isArray(list) ? list.slice(-500) : []
  }

  function setGameState(state) {
    lastGameState.value = state
    gameLog.value.push(state)
    if (gameLog.value.length > 200) gameLog.value.shift()
    if (state && state.gameType) gameType.value = state.gameType
    if (state && (state.stage === 'STOPPED' || state.stage === 'GAME_OVER')) {
      // 关键修复：在游戏停止或结束时，重置 gameType
      gameType.value = null;
      // 游戏结束 → 清空画板缓存
      drawStrokes.value = []
      drawPrivate.value = null
      gameStartInfo.value = null
    }
  }

  function setGameLog(list) {
    gameLog.value = Array.isArray(list) ? list.slice(-200) : []
    lastGameState.value = gameLog.value[gameLog.value.length - 1] || null
  }

  function setInvitation(inv) {
    if (inv && (inv.state === 'NONE' || !inv.state)) {
      invitation.value = null
    } else {
      invitation.value = inv || null
    }
  }

  function setInvitationState(state) {
    invitationState.value = state
    if (state && state.responses && invitation.value) {
      invitation.value.responses = state.responses
    }
  }

  function setGameStartInfo(msg) {
    gameStartInfo.value = msg
  }

  function appendDrawStroke(stroke) {
    if (!stroke) return
    drawStrokes.value.push(stroke)
    if (drawStrokes.value.length > 5000) {
      drawStrokes.value = drawStrokes.value.slice(-3000)
    }
  }

  function clearDrawStrokes() {
    drawStrokes.value = []
  }

  function applyDrawPrivate(msg) {
    drawPrivate.value = msg
  }

  function notifyFileChange(_ev) {
    // 由 FileView 自行重新 fetch 列表 - 这里只是占位让订阅链路完整
  }

  // 历史拉取（HTTP 兜底）
  async function loadChatHistory() {
    try {
      const res = await fetch(`${API_BASE}/chat/history`)
      if (res.ok) setMessages(await res.json())
    } catch (e) { console.warn('chat history failed:', e) }
  }

  async function loadGameHistory() {
    try {
      const res = await fetch(`${API_BASE}/game/history`)
      if (res.ok) setGameLog(await res.json())
    } catch (e) { console.warn('game history failed:', e) }
  }

  return {
    self, currentRoomId, players, messages, gameType, lastGameState, gameLog,
    invitation, invitationState, gameStartInfo,
    drawStrokes, drawPrivate, hasJoined, isParticipantInActiveGame,
    setSelf, patchSelf, clearSelf, setRoomId, applyRoom, bootstrap, ensurePresence,
    joinAs, updateName, uploadAvatar, clearAvatar, leave,
    appendMessage, setMessages,
    setGameState, setGameLog, setInvitation, setInvitationState, setGameStartInfo,
    appendDrawStroke, clearDrawStrokes, applyDrawPrivate, notifyFileChange,
    loadChatHistory, loadGameHistory
  }
})
