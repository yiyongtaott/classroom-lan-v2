<template>
  <div id="app">
    <nav>
      <router-link to="/">首页</router-link> |
      <router-link v-if="!hasKey" to="/join">加入房间</router-link>
      <template v-else>
        | <router-link to="/chat">聊天</router-link> |
        <router-link to="/game">游戏</router-link>
      </template>
    </nav>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const hasKey = computed(() => !!localStorage.getItem('roomKey'))
</script>

<style>
nav { padding: 1rem; }
nav a { margin: 0 .5rem; text-decoration: none; color: #42b983; }
</style>
