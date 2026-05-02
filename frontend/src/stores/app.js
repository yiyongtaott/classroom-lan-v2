import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用层全局状态：连接、host 信息、初始化标志位。
 * 与 roomStore 解耦：roomStore 只关心已加入用户的视图。
 */
export const useAppStore = defineStore('app', () => {
  const connected = ref(false)
  const initialized = ref(false)
  const hostNodeId = ref('')
  const hostHostname = ref('')
  const peerCount = ref(0)
  const playerCount = ref(0)
  const gameType = ref(null)
  // 当 status 报告 host=true 时设为 true（accessor 视角）
  const isHost = ref(false)
  const selfNodeId = ref('')   // 本机 IP（accessor 视角）
  const selfHostname = ref('') // 本机系统名

  function setConnected(v) { connected.value = !!v }

  function applyStatus(status) {
    if (!status) return
    if ('host' in status) isHost.value = !!status.host
    if ('nodeId' in status) selfNodeId.value = status.nodeId || ''
    if ('hostname' in status) selfHostname.value = status.hostname || ''
    if ('hostNodeId' in status) hostNodeId.value = status.hostNodeId || ''
    if ('hostHostname' in status) hostHostname.value = status.hostHostname || ''
    if ('peerCount' in status) peerCount.value = status.peerCount ?? 0
    if ('playerCount' in status) playerCount.value = status.playerCount ?? 0
    if ('gameType' in status) gameType.value = status.gameType
    initialized.value = true
  }

  function applyHostChange(msg) {
    if (msg && msg.newHostId) hostNodeId.value = msg.newHostId
  }

  return {
    connected, initialized, hostNodeId, hostHostname, peerCount, playerCount, gameType,
    isHost, selfNodeId, selfHostname,
    setConnected, applyStatus, applyHostChange
  }
})
