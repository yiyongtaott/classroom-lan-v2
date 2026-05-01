import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { API_BASE } from '../appConfig'

export const useRoomStore = defineStore('room', () => {
  // —— 本机身份（访问者视角，由 /api/status 给出）——
  const self = ref(JSON.parse(localStorage.getItem('self') || 'null'))
  const isHost = ref(false)
  const nodeId = ref('')
  const hostname = ref('')
  const hostNodeId = ref('')
  const hostHostname = ref('')
  const peerCount = ref(0)

  // —— 房间状态 ——
  const players = ref([])
  const messages = ref([])
  const gameType = ref(null)
  const lastGameState = ref(null)
  const gameLog = ref([])
  const files = ref([])

  const hasJoined = computed(() => !!self.value)
  const reconciling = ref(false)

  function setSelf(player) {
    self.value = player
    localStorage.setItem('self', JSON.stringify(player))
  }

  function clearSelf() {
    self.value = null
    localStorage.removeItem('self')
  }

  async function refreshStatus() {
    const res = await fetch(`${API_BASE}/status`)
    if (!res.ok) throw new Error(`status ${res.status}`)
    const json = await res.json()
    isHost.value = json.host
    nodeId.value = json.nodeId
    hostname.value = json.hostname
    hostNodeId.value = json.hostNodeId
    hostHostname.value = json.hostHostname
    peerCount.value = json.peerCount
    gameType.value = json.gameType
    return json
  }

  /** 拉取房间快照；若 self 不在房间里（host 重启后等），自动重新加入。 */
  async function refreshSnapshot() {
    const res = await fetch(`${API_BASE}/room`)
    if (!res.ok) throw new Error(`room ${res.status}`)
    const json = await res.json()
    players.value = json.players || []
    gameType.value = json.gameType

    // —— Bug 3: reconcile —— 本地 self 还在但 host 已经没记录 → 重新加入
    if (self.value && !players.value.find(p => p.id === self.value.id) && !reconciling.value) {
      await ensurePresence()
    }
    return json
  }

  async function ensurePresence() {
    if (!self.value || reconciling.value) return
    reconciling.value = true
    try {
      const old = self.value
      const res = await fetch(`${API_BASE}/room/players`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: old.name, hostname: old.hostname })
      })
      if (!res.ok) throw new Error(`rejoin HTTP ${res.status}`)
      const newPlayer = await res.json()
      setSelf(newPlayer)
      // 重新拉一次（不会再次触发 ensurePresence，因为 reconciling=true）
      const snap = await fetch(`${API_BASE}/room`).then(r => r.ok ? r.json() : { players: [] })
      players.value = snap.players || []
    } catch (e) {
      console.warn('[ensurePresence] failed:', e.message)
    } finally {
      reconciling.value = false
    }
  }

  async function joinAs({ name, hostname: hn }) {
    const res = await fetch(`${API_BASE}/room/players`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, hostname: hn })
    })
    if (!res.ok) throw new Error(`join failed: HTTP ${res.status}`)
    const player = await res.json()
    setSelf(player)
    await refreshSnapshot()
    return player
  }

  async function updateName(newName) {
    if (!self.value) throw new Error('not joined')
    const res = await fetch(`${API_BASE}/room/players/${self.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName })
    })
    if (!res.ok) throw new Error(`update HTTP ${res.status}`)
    const updated = await res.json()
    setSelf(updated)
    await refreshSnapshot()
    return updated
  }

  async function uploadAvatar(file) {
    if (!self.value) throw new Error('not joined')
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(`${API_BASE}/avatars/${self.value.id}`, {
      method: 'POST',
      body: fd
    })
    if (!res.ok) throw new Error(`avatar upload failed: HTTP ${res.status}`)
    const data = await res.json()
    self.value.avatar = data.avatar
    setSelf(self.value)
    await refreshSnapshot()
    return data.avatar
  }

  async function leave() {
    if (!self.value) return
    try {
      await fetch(`${API_BASE}/room/players/${self.value.id}`, { method: 'DELETE' })
    } catch {}
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
  }

  function setGameLog(list) {
    gameLog.value = Array.isArray(list) ? list.slice(-200) : []
    lastGameState.value = gameLog.value[gameLog.value.length - 1] || null
  }

  function setFiles(list) {
    files.value = list || []
  }

  // —— 历史拉取 ——
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
    self, isHost, nodeId, hostname, hostNodeId, hostHostname, peerCount, hasJoined,
    players, messages, gameType, lastGameState, gameLog, files,
    setSelf, clearSelf, refreshStatus, refreshSnapshot, ensurePresence,
    joinAs, updateName, uploadAvatar, leave,
    appendMessage, setMessages, setGameState, setGameLog, setFiles,
    loadChatHistory, loadGameHistory
  }
})
