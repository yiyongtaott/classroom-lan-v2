<template>
  <el-card class="game-wrapper" shadow="hover">
    <template #header>
      <div class="game-header">
        <span class="game-title">{{ title }}</span>
        <div class="game-actions">
          <slot name="actions"></slot>
          <el-button type="danger" size="small" @click="stopGame">停止游戏</el-button>
        </div>
      </div>
    </template>
    <div class="game-content">
      <slot></slot>
    </div>
  </el-card>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'

defineProps(['title'])
const emit = defineEmits(['stop'])

function stopGame() {
  emit('stop')
}
</script>

<style scoped>
.game-wrapper { margin: 10px; border-radius: 12px; border: none; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); }
.game-header { display: flex; justify-content: space-between; align-items: center; }
.game-title { font-weight: bold; font-size: 1.2rem; color: #303133; }
.game-content { min-height: 400px; padding: 20px; }
</style>
