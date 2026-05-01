import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { API_BASE } from '../appConfig'

export const useRoomStore = defineStore('room', () => {
  // —— 本机身份 ——
  const self = ref(JSON.parse(localStorage.getItem('self') || 'null'))     // {id, name}
  const isHost = ref(false)
  const nodeId = ref('')
  const hostNodeId = ref('')
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
    const json = await res.json()
    isHost.value = json.host
    nodeId.value = json.nodeId
    hostNodeId.value = json.hostNodeId
    peerCount.value = json.peerCount
    gameType.value = json.gameType
    return json
  }

  async function refreshSnapshot() {
    const res = await fetch(`${API_BASE}/room`)
    const json = await res.json()
    players.value = json.players || []
    gameType.value = json.gameType
    return json
  }

  async function joinAs(name) {
    const res = await fetch(`${API_BASE}/room/players`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    })
    if (!res.ok) throw new Error(`Join failed: ${res.status}`)
    const player = await res.json()
    setSelf(player)
    await refreshSnapshot()
    return player
  }

  async function leave() {
    if (!self.value) return
    await fetch(`${API_BASE}/room/players/${self.value.id}`, { method: 'DELETE' })
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
    self, isHost, nodeId, hostNodeId, peerCount, hasJoined,
    players, messages, gameType, lastGameState, files,
    setSelf, clearSelf, refreshStatus, refreshSnapshot, joinAs, leave,
    appendMessage, setGameState, setFiles
  }
})
