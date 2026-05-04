import { onBeforeUnmount } from 'vue'
import { useStomp } from './useStomp'
import { useRoomStore } from '../stores/room'
import { useAppStore } from '../stores/app'
import { useUserListStore } from '../stores/userList'
import { useToastStore } from '../stores/toast'
import { usePrivateChatStore } from '../stores/privateChat'
import { TOPIC, QUEUE, APP, INVITE_RESPONSE } from '../appConfig'

/**
 * 统一管理 WebSocket 订阅和生命周期。
 *
 * 订阅顺序（onConnect 触发时一次性注册）：
 *   /user/queue/init       初始快照（含 status / room / chatHistory / gameHistory / invitation / userStatuses）
 *   /topic/status          全局状态变更（host/peerCount/playerCount 等）
 *   /topic/room            房间快照（玩家增减触发）
 *   /topic/players         玩家列表（兼容旧广播）
 *   /topic/user.update     单玩家增量（改名 / 改头像）
 *   /topic/user.status     三状态圆点（任务 3）
 *   /topic/host            host 变更通知
 *   /topic/chat            聊天消息
 *   /topic/game.state      游戏状态
 *   /topic/game.invitation 邀请生命周期
 *   /topic/game.invitation.state 实时投票计数
 *   /topic/game.start      游戏正式开始通知（携带参与者）
 *   /topic/game.draw.canvas 你画我猜笔画
 *   /topic/file.uploaded   文件上传全员提示
 *   /topic/file.progress   文件列表刷新
 *   /user/queue/private.chat   私聊单播
 *   /user/queue/private.invite 私聊邀请单播
 *   /user/queue/game.draw      你画我猜单播（drawer 答案）
 */
export function useWebSocket() {
  const stomp = useStomp()
  const roomStore = useRoomStore()
  const appStore = useAppStore()
  const userListStore = useUserListStore()
  const toastStore = useToastStore()
  const privateChatStore = usePrivateChatStore()

  const unsubs = []

  function track(unsub) { if (unsub) unsubs.push(unsub) }

  function reportPageActive() {
    // console.log('stomp.connected.value:'+stomp.connected.value)
    if (!stomp.connected.value) return
    const active = document.visibilityState === 'visible'
    stomp.publish(APP.PAGE_ACTIVE, { active })
  }

  function onVisibilityChange() {
    reportPageActive()
  }

  function setupSubscriptions() {
    // console.log('setupSubscriptions')
    // 初始快照（每次重连都重新订阅，服务端在 SessionConnectedEvent 时单播一次）
    track(stomp.subscribe(QUEUE.INIT, (snap) => {
      // console.log('[INIT] 完整快照:', JSON.stringify(snap))
      if (!snap) return
      if (snap.status) appStore.applyStatus(snap.status)
      if (snap.room) {
        roomStore.applyRoom(snap.room)
        userListStore.applyPlayers(snap.room.players || [])
      }
      if (snap.chatHistory) roomStore.setMessages(snap.chatHistory)
      if (snap.gameHistory) roomStore.setGameLog(snap.gameHistory)
      if (snap.invitation) roomStore.setInvitation(snap.invitation)
      if (snap.userStatuses) userListStore.applyStatusMap(snap.userStatuses)
    }))

    track(stomp.subscribe(TOPIC.STATUS, (s) => appStore.applyStatus(s)))
    track(stomp.subscribe(TOPIC.ROOM, (r) => {
      roomStore.applyRoom(r)
      if (r && r.players) userListStore.applyPlayers(r.players)
    }))
    track(stomp.subscribe(TOPIC.PLAYERS, (list) => {
      if (Array.isArray(list)) userListStore.applyPlayers(list)
    }))

    track(stomp.subscribe(TOPIC.USER_UPDATE, (u) => {
      userListStore.updateUser(u)
      // 自身改名 / 改头像 → 同步 self
      if (u && roomStore.self?.id === u.id) {
        roomStore.patchSelf({
          name: u.name,
          avatar: u.avatar,
          hostname: u.hostname
        })
      }
    }))

    track(stomp.subscribe(TOPIC.USER_STATUS, (u) => {
      userListStore.updateStatus(u)
    }))

    track(stomp.subscribe(TOPIC.HOST, (msg) => {
      if (!msg) return
      appStore.applyHostChange(msg)
      // 收到 HOST_CHANGED：当前页面是旧 Host 的页面，通知并跳转
      if (msg.type === 'HOST_CHANGED' && !msg.isSelf) {
        toastStore.push({
          type: 'warn', icon: '🔁', durationMs: 4000,
          title: 'Host 已切换', body: `正在跳转到新主机 ${msg.hostIp}...`
        })
        setTimeout(() => {
          if (msg.hostIp && msg.hostPort) {
            window.location.href = `http://${msg.hostIp}:${msg.hostPort}/`
          }
        }, 2000)
      }
    }))

    track(stomp.subscribe(TOPIC.CHAT, (msg) => {
      roomStore.appendMessage(msg)
      if (msg && msg.senderId !== roomStore.self?.id && document.visibilityState !== 'visible') {
        toastStore.push({
          type: 'info', icon: '💬', durationMs: 2200,
          title: msg.sender || '新消息',
          body: msg.content
        })
      }
    }))

    track(stomp.subscribe(TOPIC.GAME_STATE, (state) => {
      roomStore.setGameState(state)
    }))

    track(stomp.subscribe(TOPIC.GAME_INVITATION, (inv) => {
      roomStore.setInvitation(inv)
    }))

    track(stomp.subscribe(TOPIC.GAME_INVITATION_STATE, (state) => {
      roomStore.setInvitationState(state)
    }))

    track(stomp.subscribe(TOPIC.GAME_START, (msg) => {
      roomStore.setGameStartInfo(msg)
    }))

    track(stomp.subscribe(TOPIC.GAME_DRAW_CANVAS, (stroke) => {
      roomStore.appendDrawStroke(stroke)
    }))

    track(stomp.subscribe(TOPIC.FILE_PROGRESS, (ev) => {
      roomStore.notifyFileChange(ev)
    }))

    track(stomp.subscribe(TOPIC.FILE_UPLOADED, (ev) => {
      if (!ev) return
      toastStore.push({
        type: 'ok', icon: '📁', durationMs: 5000,
        title: `${ev.uploaderName || '某用户'} 上传了文件`,
        body: ev.fileName,
        action: { label: '下载', url: ev.downloadUrl }
      })
    }))

    track(stomp.subscribe(QUEUE.PRIVATE_CHAT, (msg) => {
      if (!msg) return
      const peerId = msg.senderId === roomStore.self?.id ? msg.receiverId : msg.senderId
      privateChatStore.addMessage(peerId, msg)
    }))

    track(stomp.subscribe(QUEUE.PRIVATE_INVITE, (msg) => {
      if (!msg) return
      if (msg.type === 'INVITE') {
        privateChatStore.handleIncomingInvite(msg)
      } else if (msg.type === 'INVITE_RESPONSE') {
        privateChatStore.handleInviteResponse(msg)
      }
    }))

    track(stomp.subscribe(QUEUE.DRAW_PRIVATE, (msg) => {
      roomStore.applyDrawPrivate(msg)
    }))
  }

  function init() {
    stomp.connect()
    // 每次重连都重新订阅 + 重发 player.online + 重发 page-active
    const offConnect = stomp.onConnect(() => {
      // 子函数 setupSubscriptions 内部已经把订阅加入到 stomp 的 subscriptions map，
      // 重连时 useStomp 的 onConnect 会自动恢复。
      const playerId = roomStore.self?.id
      if (playerId) {
        stomp.publish(APP.PLAYER_ONLINE, { playerId })
      }
      reportPageActive()
    })
    setupSubscriptions()
    document.addEventListener('visibilitychange', onVisibilityChange)
    unsubs.push(offConnect)
  }

  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    for (const u of unsubs) {
      try { u() } catch {}
    }
  })

  // 暴露给业务组件：发起邀请响应、私聊、画板等
  function respondInvitation(playerId, response) {
    stomp.publish(APP.GAME_INVITATION_RESPOND, { playerId, response })
  }

  return {
    stomp,
    init,
    reportPageActive,
    respondInvitation,
    INVITE_RESPONSE
  }
}
