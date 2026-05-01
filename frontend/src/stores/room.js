import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { API_BASE } from '../appConfig'

export const useRoomStore = defineStore('room', () => {
  // —— 本机身份 ——
  const self = ref(JSON.parse(localStorage.getItem('self') || 'null'))
  const isHost = ref(false)
  const nodeId = ref('')        // = 本机 IP
  const hostname = ref('')      // 本机系统名
  const hostNodeId = ref('')    // 当前 Host 的 IP
  const peerCount = ref(0)

  // —— 房间状态 ——
  const players = ref([])
  const messages = ref([])
  const gameType = ref(null)
  const lastGameState = ref(null)
  const files = ref([])

  const hasJoined = computed(() => !!self.value)

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
    peerCount.value = json.peerCount
    gameType.value = json.gameType
    return json
  }

  async function refreshSnapshot() {
    const res = await fetch(`${API_BASE}/room`)
    if (!res.ok) throw new Error(`room ${res.status}`)
    const json = await res.json()
    players.value = json.players || []
    gameType.value = json.gameType
    return json
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
    if (messages.value.length > 200) messages.value.shift()
  }

  function setGameState(state) {
    lastGameState.value = state
  }

  function setFiles(list) {
    files.value = list || []
  }

  return {
    self, isHost, nodeId, hostname, hostNodeId, peerCount, hasJoined,
    players, messages, gameType, lastGameState, files,
    setSelf, clearSelf, refreshStatus, refreshSnapshot,
    joinAs, uploadAvatar, leave,
    appendMessage, setGameState, setFiles
  }
})
