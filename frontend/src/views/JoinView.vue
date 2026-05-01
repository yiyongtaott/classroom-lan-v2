<template>
  <section class="join">
    <h2>加入房间</h2>
    <p class="hint">默认昵称使用系统名 + IP，如不喜欢可自行修改。无需密钥 / Token（业务文档 §1.2）。</p>

    <form @submit.prevent="submit">
      <div class="row" style="align-items:center;gap:1rem;">
        <div class="avatar-picker" @click="pickAvatar">
          <img v-if="avatarPreview" :src="avatarPreview" alt="avatar" />
          <span v-else>选头像</span>
        </div>
        <input
          ref="avatarInput"
          type="file"
          accept="image/*"
          style="display:none"
          @change="onAvatarPick"
        />
        <div class="me-info">
          <div><strong>{{ roomStore.hostname || '本机' }}</strong></div>
          <div class="muted">{{ roomStore.nodeId || '—' }}</div>
        </div>
      </div>

      <label>
        昵称
        <input v-model.trim="name" maxlength="24" placeholder="昵称" autofocus />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '../stores/room'

const router = useRouter()
const roomStore = useRoomStore()

const name = ref('')
const avatarFile = ref(null)
const avatarPreview = ref('')
const error = ref('')
const loading = ref(false)
const avatarInput = ref(null)

const canSubmit = computed(() => name.value.length > 0)

function defaultName() {
  const remembered = localStorage.getItem('lastName')
  if (remembered) return remembered
  if (roomStore.hostname) return roomStore.hostname
  if (roomStore.nodeId) return roomStore.nodeId
  return ''
}

onMounted(async () => {
  try {
    await roomStore.refreshStatus()
  } catch {}
  if (!name.value) name.value = defaultName()
})

watch(() => roomStore.hostname, () => {
  if (!name.value) name.value = defaultName()
})

function pickAvatar() {
  if (avatarInput.value) avatarInput.value.click()
}

function onAvatarPick(e) {
  const f = e.target.files[0]
  if (!f) return
  avatarFile.value = f
  const reader = new FileReader()
  reader.onload = (ev) => { avatarPreview.value = ev.target.result }
  reader.readAsDataURL(f)
}

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await roomStore.joinAs({ name: name.value, hostname: roomStore.hostname })
    if (avatarFile.value) {
      try {
        await roomStore.uploadAvatar(avatarFile.value)
      } catch (e) {
        // 头像失败不阻塞加入
        console.warn('avatar upload failed', e)
      }
    }
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
.join { max-width: 480px; margin: 2rem auto; padding: 1.75rem; background: white; border: 1px solid #e5e7eb; border-radius: 8px; }
h2 { margin-top: 0; color: #16a34a; }
.hint { color: #6b7280; font-size: .9rem; }
label { display: block; margin: 1.25rem 0; }
input[type=text], input:not([type]) { display: block; width: 100%; padding: .55rem; margin-top: .35rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; }
.row { display: flex; gap: .5rem; margin-top: 1rem; }
.btn { padding: .55rem 1.1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; text-decoration: none; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
.error { color: #dc2626; margin-top: .75rem; font-size: .9rem; }

.avatar-picker {
  width: 64px; height: 64px; border-radius: 50%;
  border: 2px dashed #d1d5db; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  background: #f9fafb; color: #6b7280; font-size: .8rem;
  overflow: hidden; flex-shrink: 0;
}
.avatar-picker:hover { border-color: #16a34a; color: #16a34a; }
.avatar-picker img { width: 100%; height: 100%; object-fit: cover; }
.me-info { line-height: 1.4; }
.muted { color: #6b7280; font-size: .85rem; font-family: ui-monospace, monospace; }
</style>
