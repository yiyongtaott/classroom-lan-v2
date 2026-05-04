<template>
  <div class="room-list">
    <h1>房间列表</h1>
    <div v-for="room in rooms" :key="room.id" class="room-item">
      <span>房间: {{ room.id }}</span>
      <button @click="joinRoom(room.id)">进入</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '../stores/room'

const rooms = ref([{ id: 'default' }, { id: 'room1' }]) // 模拟房间数据
const router = useRouter()
const store = useRoomStore()

function joinRoom(roomId) {
  store.setRoomId(roomId)
  router.push('/game')
}
</script>
