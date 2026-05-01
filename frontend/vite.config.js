import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端默认 8080。dev 模式下 vite 跑 5173，需要 proxy 转发 /api 与 /ws 到后端。
const BACKEND = process.env.VITE_BACKEND || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/ws':  { target: BACKEND, changeOrigin: true, ws: true }
    }
  },
  build: {
    outDir: '../backend/classroomlan-app/src/main/resources/static',
    emptyOutDir: true
  }
})
