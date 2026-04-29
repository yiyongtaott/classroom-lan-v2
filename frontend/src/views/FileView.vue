<template>
  <div class="files">
    <h2>📁 文件传输</h2>

    <div class="upload-area">
      <input type="file" ref="fileInput" @change="handleFileSelect" />
      <button @click="uploadFile" :disabled="!selectedFile || uploading">
        {{ uploading ? '上传中...' : '上传文件' }}
      </button>
      <span v-if="uploadProgress > 0" class="progress">{{ uploadProgress }}%</span>
    </div>

    <div class="file-list">
      <h3>已上传文件</h3>
      <ul v-if="files.length">
        <li v-for="file in files" :key="file.id">
          <span>{{ file.name }}</span>
          <span class="size">{{ formatSize(file.size) }}</span>
          <a :href="file.url" target="_blank" class="btn-download">下载</a>
          <button @click="deleteFile(file.id)" class="btn-delete">删除</button>
        </li>
      </ul>
      <p v-else>暂无文件</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const selectedFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const files = ref([])

const fileInput = ref(null)

function handleFileSelect(e) {
  selectedFile.value = e.target.files[0]
}

async function uploadFile() {
  if (!selectedFile.value) return

  uploading.value = true
  uploadProgress.value = 0

  const formData = new FormData()
  formData.append('file', selectedFile.value)

  try {
    const xhr = new XMLHttpRequest()
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    })

    xhr.open('POST', '/api/files/upload')
    xhr.setRequestHeader('roomKey', localStorage.getItem('roomKey'))

    xhr.onload = () => {
      uploading.value = false
      if (xhr.status === 200) {
        const result = JSON.parse(xhr.responseText)
        files.value.push(result)
        selectedFile.value = null
        fileInput.value.value = ''
      }
    }

    xhr.send(formData)
  } catch (e) {
    uploading.value = false
    alert('上传失败：' + e.message)
  }
}

function deleteFile(id) {
  // TODO: 调用删除 API
  files.value = files.value.filter(f => f.id !== id)
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.files {
  max-width: 800px;
  margin: 2rem auto;
  padding: 1rem;
}

.upload-area {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  border: 2px dashed #ccc;
  border-radius: 8px;
  margin-bottom: 2rem;
}

.progress {
  color: #42b983;
  font-weight: bold;
}

.file-list ul {
  list-style: none;
  padding: 0;
}

.file-list li {
  display: flex;
  align-items: center;
  padding: 0.75rem;
  border-bottom: 1px solid #eee;
  gap: 1rem;
}

.size {
  color: #666;
  margin-left: auto;
}

.btn-download {
  color: #42b983;
  text-decoration: none;
  margin-right: 0.5rem;
}

.btn-delete {
  padding: 0.25rem 0.5rem;
  background: #f56c6c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
