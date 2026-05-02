<template>
  <section class="files">
    <h2>文件传输</h2>
    <p class="hint">选择文件后自动上传。文件存于 Host 本地。Host 切换会丢失元数据。</p>

    <div class="upload" @drop.prevent="onDrop" @dragover.prevent>
      <input ref="fileInput" type="file" @change="onPick" :disabled="uploading" />
      <span v-if="uploading" class="progress">上传中 {{ progress }}%</span>
      <span v-else class="hint-inline">选择文件或拖拽到此处自动开始</span>
    </div>
    <div v-if="error" class="error">{{ error }}</div>

    <h3>已上传 ({{ list.length }})</h3>
    <ul class="list" v-if="list.length">
      <li v-for="f in list" :key="f.id">
        <span class="name">{{ f.name }}</span>
        <span class="size">{{ formatSize(f.size) }}</span>
        <a class="btn small" :href="`/api/files/${f.id}`" target="_blank">下载</a>
        <button class="btn small danger" @click="remove(f.id)">删除</button>
      </li>
    </ul>
    <div v-else class="empty">尚无文件</div>
  </section>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useStomp } from '../composables/useStomp'
import { useToastStore } from '../stores/toast'
import { API_BASE, TOPIC } from '../appConfig'

const stomp = useStomp()
const toastStore = useToastStore()

const fileInput = ref(null)
const uploading = ref(false)
const progress = ref(0)
const error = ref('')
const list = ref([])
let unsub = null

async function refresh() {
  try {
    const res = await fetch(`${API_BASE}/files`)
    list.value = await res.json()
  } catch (e) { console.warn('refresh files failed', e) }
}

function onPick(e) {
  const f = e.target.files[0]
  if (f) startUpload(f)
}

function onDrop(e) {
  const f = e.dataTransfer?.files?.[0]
  if (f) startUpload(f)
}

function startUpload(file) {
  uploading.value = true
  progress.value = 0
  error.value = ''

  const formData = new FormData()
  formData.append('file', file)

  const xhr = new XMLHttpRequest()
  xhr.upload.addEventListener('progress', (e) => {
    if (e.lengthComputable) {
      progress.value = Math.round((e.loaded / e.total) * 100)
    }
  })
  xhr.onload = () => {
    uploading.value = false
    if (xhr.status >= 200 && xhr.status < 300) {
      if (fileInput.value) fileInput.value.value = ''
      toastStore.push({ type: 'ok', icon: '📁', title: '上传完成', body: file.name })
      refresh()
    } else {
      error.value = `上传失败: HTTP ${xhr.status}`
    }
  }
  xhr.onerror = () => {
    uploading.value = false
    error.value = '上传失败：网络错误'
  }
  xhr.open('POST', `${API_BASE}/files/upload`)
  xhr.send(formData)
}

async function remove(id) {
  await fetch(`${API_BASE}/files/${id}`, { method: 'DELETE' })
  refresh()
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(async () => {
  await refresh()
  unsub = stomp.subscribe(TOPIC.FILE_PROGRESS, () => refresh())
})

onBeforeUnmount(() => {
  if (unsub) unsub()
})
</script>

<style scoped>
.files { max-width: 900px; margin: 0 auto; }
h2 { color: #16a34a; }
.hint { color: #6b7280; font-size: .9rem; }
.upload {
  display: flex; align-items: center; gap: .75rem;
  padding: 1.25rem; background: white; border: 2px dashed #d1d5db;
  border-radius: 8px; margin: 1rem 0;
  transition: border-color .15s, background .15s;
}
.upload:hover { border-color: #16a34a; background: #f0fdf4; }
.upload input[type=file] { flex: 1; }
.hint-inline { color: #9ca3af; font-size: .85rem; }
.progress { color: #16a34a; font-weight: 600; }
.error { color: #dc2626; padding: .5rem 0; }
.list { list-style: none; padding: 0; background: white; border: 1px solid #e5e7eb; border-radius: 8px; }
.list li { display: grid; grid-template-columns: 1fr auto auto auto; gap: .75rem; align-items: center; padding: .65rem 1rem; border-bottom: 1px solid #f3f4f6; }
.list li:last-child { border-bottom: none; }
.name { font-weight: 500; word-break: break-all; }
.size { color: #6b7280; font-size: .85rem; }
.btn { padding: .35rem .75rem; border-radius: 6px; border: 1px solid #d1d5db; background: white; color: #1f2937; text-decoration: none; font-size: .85rem; }
.btn.small { padding: .25rem .6rem; font-size: .8rem; }
.btn.danger { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
.empty { color: #9ca3af; padding: 2rem; text-align: center; background: white; border: 1px solid #e5e7eb; border-radius: 8px; }
</style>
