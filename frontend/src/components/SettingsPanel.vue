<template>
  <aside class="settings-panel">
    <header>
      <h3>账号</h3>
    </header>

    <div v-if="!roomStore.hasJoined" class="empty">
      <p>请先 <router-link to="/join">加入房间</router-link></p>
    </div>

    <div v-else class="content">
      <div class="profile">
        <div class="avatar-wrap" @click="pickAvatar" title="点击更换头像（自动上传）">
          <img v-if="roomStore.self?.avatar" :src="roomStore.self.avatar" alt="avatar" @error="onAvatarFail" />
          <span v-else>{{ initial }}</span>
          <div class="avatar-mask" v-if="avatarUploading">{{ avatarUploading }}%</div>
        </div>
        <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarPick" />
        <button class="btn small" @click="pickAvatar" :disabled="avatarUploading">
          {{ avatarUploading ? '上传中…' : '更换头像' }}
        </button>
        <button class="btn small danger" @click="clearAvatar" v-if="roomStore.self?.avatar && !avatarUploading">移除</button>
      </div>

      <label>
        昵称
        <div class="row">
          <input v-model.trim="name" maxlength="24" />
          <button class="btn small primary" @click="saveName" :disabled="!canSave || nameSaving">
            {{ nameSaving ? '…' : '保存' }}
          </button>
        </div>
      </label>

      <dl class="meta">
        <dt>玩家 ID</dt><dd>{{ shortId(roomStore.self?.id) }}</dd>
        <dt>本机 IP</dt><dd>{{ roomStore.self?.ip || roomStore.nodeId || '—' }}</dd>
        <dt>系统名</dt><dd>{{ roomStore.self?.hostname || roomStore.hostname || '—' }}</dd>
        <dt>角色</dt>
        <dd>
          <span :class="['role', roomStore.isHost ? 'host' : 'client']">
            {{ roomStore.isHost ? 'HOST' : 'CUSTOMER' }}
          </span>
        </dd>
      </dl>

      <button class="btn block danger" @click="leave">退出房间</button>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '../stores/room'
import { useToastStore } from '../stores/toast'

const router = useRouter()
const roomStore = useRoomStore()
const toastStore = useToastStore()

const name = ref('')
const avatarInput = ref(null)
const avatarUploading = ref(0)
const nameSaving = ref(false)

const canSave = computed(() => name.value.length > 0 && name.value !== roomStore.self?.name)
const initial = computed(() => (roomStore.self?.name || '?').charAt(0).toUpperCase())

function shortId(id) { return id ? (id.length > 8 ? id.slice(0, 8) : id) : '—' }

function pickAvatar() {
  if (avatarUploading.value) return
  if (avatarInput.value) avatarInput.value.click()
}

async function onAvatarPick(e) {
  const file = e.target.files[0]
  if (!file) return
  avatarUploading.value = 1
  try {
    await roomStore.uploadAvatar(file)
    avatarUploading.value = 0
    toastStore.push({ type: 'ok', icon: '🖼️', title: '头像已更新' })
  } catch (err) {
    avatarUploading.value = 0
    toastStore.push({ type: 'err', icon: '⚠️', title: '上传失败', body: err.message })
  } finally {
    if (avatarInput.value) avatarInput.value.value = ''
  }
}

function onAvatarFail() {
  if (roomStore.self) roomStore.self.avatar = null
}

async function clearAvatar() {
  await roomStore.clearAvatar()
}

async function saveName() {
  if (!canSave.value) return
  nameSaving.value = true
  try {
    await roomStore.updateName(name.value)
    localStorage.setItem('lastName', name.value)
    toastStore.push({ type: 'ok', icon: '✏️', title: '昵称已更新' })
  } catch (err) {
    toastStore.push({ type: 'err', icon: '⚠️', title: '保存失败', body: err.message })
  } finally {
    nameSaving.value = false
  }
}

async function leave() {
  if (!confirm('退出房间？头像和聊天记录在 Host 端仍会保留。')) return
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
.settings-panel {
  display: flex; flex-direction: column;
  height: 100%;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}
header {
  padding: .85rem 1rem; border-bottom: 1px solid #f3f4f6;
}
header h3 { margin: 0; color: #16a34a; font-size: 1rem; }
.empty { padding: 2rem 1rem; color: #6b7280; text-align: center; }
.content { padding: 1rem; overflow-y: auto; display: flex; flex-direction: column; gap: 1rem; }

.profile { display: flex; flex-direction: column; align-items: center; gap: .5rem; }
.avatar-wrap {
  width: 80px; height: 80px; border-radius: 50%;
  background: linear-gradient(135deg, #4ade80 0%, #16a34a 100%);
  color: white; font-weight: 700; font-size: 1.8rem;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; overflow: hidden; position: relative;
  border: 3px solid #f3f4f6;
}
.avatar-wrap:hover { border-color: #16a34a; }
.avatar-wrap img { width: 100%; height: 100%; object-fit: cover; }
.avatar-mask {
  position: absolute; inset: 0;
  background: rgba(0,0,0,.5);
  color: white; display: flex; align-items: center; justify-content: center;
  font-size: 1rem;
}

label { display: block; font-size: .85rem; color: #6b7280; }
.row { display: flex; gap: .35rem; margin-top: .25rem; }
input { flex: 1; padding: .45rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: .9rem; }

.btn { padding: .4rem .8rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; font-size: .85rem; }
.btn.small { padding: .35rem .7rem; }
.btn.primary { background: #16a34a; color: white; border-color: #16a34a; }
.btn.primary:disabled { background: #9ca3af; border-color: #9ca3af; cursor: not-allowed; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
.btn.block { width: 100%; }

.meta { display: grid; grid-template-columns: auto 1fr; gap: .25rem .5rem; margin: 0; }
.meta dt { color: #6b7280; font-size: .8rem; }
.meta dd { margin: 0; font-family: ui-monospace, monospace; font-size: .8rem; word-break: break-all; }
.role { font-size: .7rem; padding: .12rem .45rem; border-radius: 4px; font-weight: 600; }
.role.host { background: #dcfce7; color: #15803d; }
.role.client { background: #dbeafe; color: #1d4ed8; }
</style>
