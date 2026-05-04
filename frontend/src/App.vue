<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div class="brand">📡 ClassroomLAN <span class="ver">v2</span></div>
      <el-menu mode="horizontal" :router="true" :default-active="activePath">
        <el-menu-item index="/">主页</el-menu-item>
        <el-menu-item index="/rooms" v-if="!roomStore.hasJoined">房间</el-menu-item>
        <el-menu-item index="/game" v-if="roomStore.hasJoined">游戏</el-menu-item>
      </el-menu>
      <div class="status-pill">
        <el-tag :type="stomp.connected.value ? 'success' : 'danger'">
          {{ stomp.connected.value ? '在线' : '离线' }}
        </el-tag>
      </div>
    </el-header>
    <el-container>
      <el-aside width="300px" v-if="roomStore.hasJoined" class="left-col">
        <ChatPanel />
      </el-aside>
      <el-main class="center-col">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoomStore } from './stores/room'
import { useStomp } from './composables/useStomp'
import { useRoute } from 'vue-router'
import ChatPanel from './components/ChatPanel.vue'

const roomStore = useRoomStore()
const stomp = useStomp()
const route = useRoute()
const activePath = computed(() => route.path)
</script>

<style>
.app-layout { height: 100vh; }
.app-header { background: #fff; border-bottom: 1px solid #dcdfe6; display: flex; align-items: center; justify-content: space-between; }
.brand { font-weight: bold; color: #409eff; font-size: 1.2rem; }
.left-col { border-right: 1px solid #dcdfe6; background: #fafafa; }
.center-col { background: #f0f2f5; padding: 20px; }
</style>
