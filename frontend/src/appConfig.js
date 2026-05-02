// 前端运行时配置 - 与后端 application.yml 对齐。

export const API_BASE = '/api'
export const WS_ENDPOINT = '/ws'

export const TOPIC = {
  CHAT: '/topic/chat',
  GAME_STATE: '/topic/game.state',
  GAME_INVITATION: '/topic/game.invitation',
  PLAYERS: '/topic/players',
  FILE_PROGRESS: '/topic/file.progress'
}

export const APP = {
  CHAT_SEND: '/app/chat',
  GAME_START: '/app/game.start',
  GAME_ACTION: '/app/game.action',
  GAME_STOP: '/app/game.stop',
  GAME_INVITATION_RESPOND: '/app/game.invitation.respond',
  PLAYER_ONLINE: '/app/player.online'
}

export const PLAYER_STATUS = {
  ONLINE: 'ONLINE',
  PAGE_CLOSED: 'PAGE_CLOSED',
  OFFLINE: 'OFFLINE'
}
