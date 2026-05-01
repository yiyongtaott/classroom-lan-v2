<template>
  <section class="join">
    <h2>加入房间</h2>
    <p class="hint">输入昵称后即可加入。无需密钥 / Token（业务文档 §1.2）。</p>
    <form @submit.prevent="submit">
      <label>
        昵称
        <input v-model.trim="name" maxlength="16" placeholder="例如：小明" autofocus />
      </label>
      <div class="row">
        <button type="submit" class="btn primary" :disabled="!canSubmit || loading">
          {{ loading ? '加入中…' : '加入' }}
        </button>
        <router-link to="/" class="btn">返回</router-link>
      </div>
      <div v-if="error" class="error">{{ error }}</div>
    </form>
  </section>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '../stores/room'

const router = useRouter()
const roomStore = useRoomStore()

const name = ref(localStorage.getItem('lastName') || '')
const error = ref('')
const loading = ref(false)

const canSubmit = computed(() => name.value.length > 0)

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await roomStore.joinAs(name.value)
    localStorage.setItem('lastName', name.value)
    router.push('/chat')
  } catch (e) {
    error.value = '加入失败：' + e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.join { max-width: 420px; margin: 2rem auto; padding: 1.75rem; background: white; border: 1px solid #e5e7eb; border-radius: 8px; }
h2 { margin-top: 0; color: #16a34a; }
.hint { color: #6b7280; font-size: .9rem; }
label { display: block; margin: 1.25rem 0; }
input { display: block; width: 100%; padding: .55rem; margin-top: .35rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; }
.row { display: flex; gap: .5rem; margin-top: 1rem; }
.btn { padding: .55rem 1.1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; text-decoration: none; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
.error { color: #dc2626; margin-top: .75rem; font-size: .9rem; }
</style>
