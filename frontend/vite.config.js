import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true
  },
  build: {
    outDir: '../backend/classroomlan-app/src/main/resources/static',
    emptyOutDir: true
  }
})
