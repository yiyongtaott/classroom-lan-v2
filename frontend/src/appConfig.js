// 前端运行时配置 - 与后端 application.yml 对齐。
// 业务文档 §1.2：完全移除安全机制 → 不再有 token/roomKey 头。

export const API_BASE = '/api'
export const WS_ENDPOINT = '/ws'

export const TOPIC = {
  CHAT: '/topic/chat',
  GAME_STATE: '/topic/game.state',
  FILE_PROGRESS: '/topic/file.progress'
}

export const APP = {
  CHAT_SEND: '/app/chat',
  GAME_START: '/app/game.start',
  GAME_ACTION: '/app/game.action',
  GAME_STOP: '/app/game.stop'
}
