<template>
  <div class="home">
    <h1>Classroom LAN v2</h1>
    <p>教室局域网实时协作平台</p>
    <div class="actions">
      <router-link v-if="!hasKey" to="/join" class="btn">加入房间</router-link>
      <router-link v-else to="/chat" class="btn">进入聊天室</router-link>
    </div>
    <div v-if="hasKey" class="room-info">
      <p>当前房间密钥: <code>{{ roomKey }}</code></p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const roomKey = ref(localStorage.getItem('roomKey') || '')
const hasKey = computed(() => !!roomKey.value)
</script>

<style scoped>
.home {
  max-width: 600px;
  margin: 3rem auto;
  text-align: center;
}
.home h1 {
  color: #42b983;
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}
.actions {
  margin-top: 2rem;
}
.btn {
  display: inline-block;
  padding: 0.75rem 2rem;
  background: #42b983;
  color: white;
  border-radius: 6px;
  text-decoration: none;
  font-weight: 500;
  transition: background 0.2s;
}
.btn:hover {
  background: #369c70;
}
.room-info {
  margin-top: 2rem;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 6px;
}
.room-info code {
  font-family: monospace;
  background: #e0e0e0;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}
</style>
