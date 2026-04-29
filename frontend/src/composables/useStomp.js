import { Client } from '@stomp/stompjs'
import { ref } from 'vue'

export function useStomp() {
  const client = new Client({
    brokerURL: `ws://${location.host}/ws`,
    connectHeaders: { roomKey: localStorage.getItem('roomKey') },
    debug: () => {},
    onConnect: () => { connected.value = true },
    onDisconnect: () => { connected.value = false },
  })

  const connected = ref(false)

  // 订阅、发送等封装
  function subscribe(destination, callback) {
    client.subscribe(destination, callback)
  }

  function publish(destination, body) {
    client.publish({ destination, body: JSON.stringify(body) })
  }

  function connect() {
    if (!client.connected) {
      client.activate()
    }
  }

  function disconnect() {
    if (client.connected) {
      client.deactivate()
    }
  }

  return { client, connected, connect, disconnect, subscribe, publish }
}
