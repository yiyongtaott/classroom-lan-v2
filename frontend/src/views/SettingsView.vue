<template>
  <section class="settings">
    <h2>账号设置</h2>
    <p v-if="!roomStore.hasJoined" class="hint">请先 <router-link to="/join">加入房间</router-link>，再来这里修改账号信息。</p>

    <div v-else class="card">
      <div class="profile">
        <div class="avatar-picker" @click="pickAvatar">
          <img v-if="avatarPreview || roomStore.self?.avatar"
               :src="avatarPreview || roomStore.self.avatar"
               alt="avatar"
               @error="onAvatarLoadError" />
          <span v-else>{{ initial }}</span>
        </div>
        <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarPick" />
        <div class="profile-info">
          <button class="btn small" @click="pickAvatar">更换头像</button>
          <button class="btn small" @click="uploadAvatar" :disabled="!avatarFile || avatarUploading">
            {{ avatarUploading ? '上传中…' : '保存头像' }}
          </button>
          <button class="btn small danger" @click="removeAvatar" v-if="roomStore.self?.avatar">移除头像</button>
        </div>
      </div>

      <hr />

      <label>
        昵称
        <input v-model.trim="name" maxlength="24" />
      </label>
      <button class="btn primary" @click="saveName" :disabled="!canSaveName || nameSaving">
        {{ nameSaving ? '保存中…' : '保存昵称' }}
      </button>

      <hr />

      <dl class="meta">
        <dt>玩家 ID</dt><dd>{{ roomStore.self?.id }}</dd>
        <dt>本机 IP</dt><dd>{{ roomStore.self?.ip || roomStore.nodeId }}</dd>
        <dt>系统名</dt><dd>{{ roomStore.self?.hostname || roomStore.hostname || '—' }}</dd>
      </dl>

      <hr />

      <button class="btn danger" @click="leave">退出房间</button>

      <div v-if="msg" :class="['msg-line', msgType]">{{ msg }}</div>
    </div>
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
const avatarUploading = ref(false)
const nameSaving = ref(false)
const avatarInput = ref(null)
const msg = ref('')
const msgType = ref('info')
let msgTimer = null

const canSaveName = computed(() => {
  return name.value.length > 0 && name.value !== roomStore.self?.name
})

const initial = computed(() => (roomStore.self?.name || '?').charAt(0).toUpperCase())

function flash(text, type = 'info') {
  msg.value = text
  msgType.value = type
  if (msgTimer) clearTimeout(msgTimer)
  msgTimer = setTimeout(() => { msg.value = '' }, 2500)
}

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

function onAvatarLoadError() {
  // 头像 URL 读取失败：清空 self.avatar 让下次刷新重拉
  if (roomStore.self) {
    roomStore.self.avatar = null
  }
}

async function uploadAvatar() {
  if (!avatarFile.value) return
  avatarUploading.value = true
  try {
    await roomStore.uploadAvatar(avatarFile.value)
    avatarFile.value = null
    avatarPreview.value = ''
    if (avatarInput.value) avatarInput.value.value = ''
    flash('头像已更新', 'ok')
  } catch (e) {
    flash('头像上传失败：' + e.message, 'err')
  } finally {
    avatarUploading.value = false
  }
}

async function removeAvatar() {
  // 后端没有删除接口；直接清空 player.avatar 字段
  try {
    const res = await fetch(`/api/room/players/${roomStore.self.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ avatar: '' })
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const updated = await res.json()
    roomStore.setSelf(updated)
    await roomStore.refreshSnapshot()
    flash('已移除头像', 'ok')
  } catch (e) {
    flash('移除失败：' + e.message, 'err')
  }
}

async function saveName() {
  if (!canSaveName.value) return
  nameSaving.value = true
  try {
    await roomStore.updateName(name.value)
    localStorage.setItem('lastName', name.value)
    flash('昵称已更新', 'ok')
  } catch (e) {
    flash('保存失败：' + e.message, 'err')
  } finally {
    nameSaving.value = false
  }
}

async function leave() {
  if (!confirm('确定要退出当前房间吗？')) return
  await roomStore.leave()
  router.push('/')
}

onMounted(() => {
  if (roomStore.self) name.value = roomStore.self.name || ''
})

watch(() => roomStore.self?.name, (v) => {
  if (v && !name.value) name.value = v
})
</script>

<style scoped>
.settings { max-width: 600px; margin: 0 auto; }
h2 { color: #16a34a; margin-bottom: 1rem; }
.hint { color: #6b7280; }
.card { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 1.5rem; }
.profile { display: flex; align-items: center; gap: 1.5rem; }
.avatar-picker {
  width: 96px; height: 96px; border-radius: 50%;
  border: 2px solid #e5e7eb; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #4ade80 0%, #16a34a 100%);
  color: white; font-weight: 700; font-size: 2rem;
  overflow: hidden; flex-shrink: 0;
}
.avatar-picker:hover { border-color: #16a34a; }
.avatar-picker img { width: 100%; height: 100%; object-fit: cover; }
.profile-info { display: flex; flex-direction: column; gap: .5rem; }
hr { border: none; border-top: 1px solid #f3f4f6; margin: 1.5rem 0; }
label { display: block; margin-bottom: .75rem; }
input { display: block; width: 100%; padding: .55rem; margin-top: .35rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; }
.btn { padding: .55rem 1.1rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; }
.btn.small { padding: .35rem .8rem; font-size: .9rem; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
.meta { display: grid; grid-template-columns: auto 1fr; gap: .25rem .75rem; margin: 0; }
.meta dt { color: #6b7280; font-size: .85rem; }
.meta dd { margin: 0; font-family: ui-monospace, monospace; word-break: break-all; }
.msg-line { margin-top: 1rem; padding: .5rem .75rem; border-radius: 4px; font-size: .9rem; }
.msg-line.info { background: #eff6ff; color: #1d4ed8; }
.msg-line.ok { background: #dcfce7; color: #15803d; }
.msg-line.err { background: #fee2e2; color: #991b1b; }
</style>
