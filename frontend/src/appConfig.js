// 应用配置常量 - 与后端保持一致
export const WS_ENDPOINT = '/ws'
export const API_BASE = '/api'

export const STOMP_HEADERS = {
  'roomKey': localStorage.getItem('roomKey') || ''
}

export const BROKER_TOPICS = {
  CHAT: '/topic/chat',
  GAME_STATE: '/topic/game.state',
  PRIVATE: '/user/queue/private'
}

export const APP_ENDPOINTS = {
  CHAT_SEND: '/app/chat',
  GAME_ACTION: '/app/game.action'
}
