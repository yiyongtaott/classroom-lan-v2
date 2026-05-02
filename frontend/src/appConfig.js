// 前端运行时配置 - 与后端 WebSocketConfig 对齐。

export const API_BASE = '/api'
export const WS_ENDPOINT = '/ws'

// 广播频道
export const TOPIC = {
  CHAT: '/topic/chat',
  GAME_STATE: '/topic/game.state',
  GAME_START: '/topic/game.start',
  GAME_INVITATION: '/topic/game.invitation',
  GAME_INVITATION_STATE: '/topic/game.invitation.state',
  PLAYERS: '/topic/players',
  STATUS: '/topic/status',
  ROOM: '/topic/room',
  USER_UPDATE: '/topic/user.update',
  USER_STATUS: '/topic/user.status',
  HOST: '/topic/host',
  FILE_PROGRESS: '/topic/file.progress',
  FILE_UPLOADED: '/topic/file.uploaded',
  GAME_DRAW_CANVAS: '/topic/game.draw.canvas'
}

// 单播频道（需要 stompjs 拼接 /user{queue}）
export const QUEUE = {
  INIT: '/user/queue/init',
  PRIVATE_CHAT: '/user/queue/private.chat',
  PRIVATE_INVITE: '/user/queue/private.invite',
  DRAW_PRIVATE: '/user/queue/game.draw'
}

// 客户端 → 服务端
export const APP = {
  CHAT_SEND: '/app/chat',
  GAME_START: '/app/game.start',
  GAME_ACTION: '/app/game.action',
  GAME_STOP: '/app/game.stop',
  GAME_INVITATION_RESPOND: '/app/game.invitation.respond',
  PLAYER_ONLINE: '/app/player.online',
  PAGE_ACTIVE: '/app/user.page-active',
  // 私聊
  PRIVATE_INVITE: '/app/private.invite',
  PRIVATE_INVITE_RESPOND: '/app/private.invite.respond',
  PRIVATE_CHAT_SEND: '/app/private.chat.send',
  // 你画我猜
  DRAW_SELECT: '/app/game.draw.select',
  DRAW_STROKE: '/app/game.draw.stroke',
  DRAW_GUESS: '/app/game.draw.guess',
  DRAW_CLEAR: '/app/game.draw.clear'
}

export const PLAYER_STATUS = {
  ONLINE: 'ONLINE',
  PAGE_CLOSED: 'PAGE_CLOSED',
  OFFLINE: 'OFFLINE'
}

export const INVITE_RESPONSE = { ACCEPT: 'ACCEPT', DECLINE: 'DECLINE', FORCE: 'FORCE' }
