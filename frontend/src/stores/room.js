import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useRoomStore = defineStore('room', () => {
  const roomKey = ref(localStorage.getItem('roomKey') || '')
  const players = ref([])
  const messages = ref([])
  const connected = ref(false)
  const gameType = ref(null)

  function addPlayer(player) {
    if (!players.value.find(p => p.id === player.id)) {
      players.value.push(player)
    }
  }

  function removePlayer(playerId) {
    players.value = players.value.filter(p => p.id !== playerId)
  }

  function addMessage(msg) {
    messages.value.push(msg)
    // 最多保留100条
    if (messages.value.length > 100) {
      messages.value.shift()
    }
  }

  function clearChat() {
    messages.value = []
  }

  function setConnected(status) {
    connected.value = status
  }

  function setGameType(type) {
    gameType.value = type
  }

  return {
    roomKey,
    players,
    messages,
    connected,
    gameType,
    addPlayer,
    removePlayer,
    addMessage,
    clearChat,
    setConnected,
    setGameType
  }
})
