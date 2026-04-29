<template>
  <div class="join">
    <h2>加入房间</h2>
    <form @submit.prevent="join">
      <div class="form-group">
        <label for="roomKey">房间密钥</label>
        <input
          id="roomKey"
          v-model="roomKey"
          type="text"
          placeholder="请输入房间密钥"
          autocomplete="off"
        />
      </div>
      <div class="form-group">
        <label for="nickname">昵称</label>
        <input
          id="nickname"
          v-model="nickname"
          type="text"
          placeholder="请输入昵称"
          autocomplete="off"
        />
      </div>
      <button type="submit" :disabled="!canJoin" class="btn">进入</button>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const roomKey = ref('')
const nickname = ref('')
const error = ref('')

const canJoin = computed(() => roomKey.value && nickname.value)

async function join() {
  error.value = ''
  try {
    // 保存到本地
    localStorage.setItem('roomKey', roomKey.value)
    localStorage.setItem('nickname', nickname.value)

    // 连接 WebSocket（useStomp 需提前注入）
    // 连接成功后跳转到聊天页
    router.push('/chat')
  } catch (e) {
    error.value = '加入失败：' + e.message
  }
}
</script>

<style scoped>
.join {
  max-width: 400px;
  margin: 3rem auto;
  padding: 2rem;
  border: 1px solid #eee;
  border-radius: 8px;
}
.form-group {
  margin-bottom: 1rem;
  text-align: left;
}
label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
}
input {
  width: 100%;
  padding: 0.5rem;
  font-size: 1rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}
button.btn {
  width: 100%;
  padding: 0.6rem;
  background: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}
button:disabled {
  background: #aaa;
  cursor: not-allowed;
}
.error {
  color: red;
  margin-top: 0.5rem;
}
</style>
