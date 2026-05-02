import { Client } from '@stomp/stompjs'
import { ref } from 'vue'
import { WS_ENDPOINT } from '../appConfig'

/**
 * STOMP 单例 composable - 进程内多个组件共享同一个连接。
 *
 * 重要语义：
 *   - reconnectDelay: 3s 自动重连，覆盖 Host 重选 / 短暂断网。
 *   - 重连成功后所有 subscribe 自动恢复（subscriptions 持有回调集）。
 *   - subscribe(destination, cb) 返回 unsub 函数；解订阅不影响其他订阅者。
 *   - publish 在未连接时丢弃并打印警告，避免阻塞业务。
 */
let client = null
const connected = ref(false)
const reconnectAttempt = ref(0)
const subscriptions = new Map() // destination → Set<callback>
const onConnectCallbacks = new Set()

function buildBrokerUrl() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}${WS_ENDPOINT}`
}

function ensureClient() {
  if (client) return client
  client = new Client({
    brokerURL: buildBrokerUrl(),
    reconnectDelay: 3000,
    debug: () => {},
    onConnect: () => {
      connected.value = true
      reconnectAttempt.value = 0
      // 重连后恢复订阅
      for (const [dest, callbacks] of subscriptions.entries()) {
        client.subscribe(dest, (frame) => {
          const body = parseBody(frame.body)
          for (const cb of callbacks) cb(body, frame)
        })
      }
      // 触发外部 connect 回调（每次重连都触发）
      for (const cb of onConnectCallbacks) {
        try { cb() } catch (e) { console.warn('[STOMP] onConnect cb error', e) }
      }
    },
    onDisconnect: () => { connected.value = false },
    onWebSocketClose: () => {
      connected.value = false
      reconnectAttempt.value++
    },
    onStompError: (frame) => {
      console.error('[STOMP] error', frame.headers['message'], frame.body)
    }
  })
  return client
}

function parseBody(raw) {
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return raw }
}

export function useStomp() {
  ensureClient()

  function connect() {
    if (!client.active) client.activate()
  }

  function disconnect() {
    if (client && client.active) client.deactivate()
  }

  function subscribe(destination, callback) {
    let set = subscriptions.get(destination)
    if (!set) {
      set = new Set()
      subscriptions.set(destination, set)
      if (client.connected) {
        client.subscribe(destination, (frame) => {
          const body = parseBody(frame.body)
          for (const cb of subscriptions.get(destination) || []) cb(body, frame)
        })
      }
    }
    set.add(callback)
    return () => {
      const s = subscriptions.get(destination)
      if (s) s.delete(callback)
    }
  }

  function publish(destination, payload) {
    if (!client.connected) {
      console.warn('[STOMP] not connected, dropping →', destination)
      return false
    }
    client.publish({
      destination,
      body: typeof payload === 'string' ? payload : JSON.stringify(payload)
    })
    return true
  }

  /** 连接建立时回调（每次重连都会触发）。 */
  function onConnect(cb) {
    onConnectCallbacks.add(cb)
    if (client.connected) {
      try { cb() } catch (e) { console.warn(e) }
    }
    return () => onConnectCallbacks.delete(cb)
  }

  return { connected, reconnectAttempt, connect, disconnect, subscribe, publish, onConnect }
}
