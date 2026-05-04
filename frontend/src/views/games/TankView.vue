<template>
  <GameBaseView title="🎮 坦克大战" @stop="stop">
    <div class="game-container">
      <el-empty description="游戏逻辑开发中..." />
    </div>
  </GameBaseView>
</template>

<script setup>
import { useStomp } from '../../composables/useStomp'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
function stop() { stomp.publish(APP.GAME_STOP, {}) }
</script>
