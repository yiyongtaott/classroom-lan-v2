import { Client } from '@stomp/stompjs'
import { ref } from 'vue'
import { WS_ENDPOINT } from '../appConfig'

// 进程内单例 - 多个组件共享同一个 STOMP 连接。
let client = null
const connected = ref(false)
const subscriptions = new Map()  // destination → Set<callback>

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
      // 重连后恢复订阅
      for (const [dest, callbacks] of subscriptions.entries()) {
        client.subscribe(dest, (frame) => {
          const body = parseBody(frame.body)
          for (const cb of callbacks) cb(body, frame)
        })
      }
    },
    onDisconnect: () => { connected.value = false },
    onWebSocketClose: () => { connected.value = false },
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
    if (!client.active) {
      client.activate()
    }
  }

  function disconnect() {
    if (client && client.active) {
      client.deactivate()
    }
  }

  function subscribe(destination, callback) {
    let set = subscriptions.get(destination)
    if (!set) {
      set = new Set()
      subscriptions.set(destination, set)
      if (client.connected) {
        client.subscribe(destination, (frame) => {
          const body = parseBody(frame.body)
          for (const cb of subscriptions.get(destination) || []) {
            cb(body, frame)
          }
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
      return
    }
    client.publish({
      destination,
      body: typeof payload === 'string' ? payload : JSON.stringify(payload)
    })
  }

  return { connected, connect, disconnect, subscribe, publish }
}
