<template>
  <GameBaseView title="⚔️ 坦克大战" @stop="stop">
    <div class="game-container">
      <div class="canvas-area">
        <div v-for="tank in tanks" :key="tank.id" class="tank" :style="{ left: tank.x + 'px', top: tank.y + 'px' }">
          TANK
        </div>
      </div>
      <div class="controls">
        <el-button @click="move(0, -10)">上</el-button>
        <el-button @click="move(0, 10)">下</el-button>
        <el-button @click="move(-10, 0)">左</el-button>
        <el-button @click="move(10, 0)">右</el-button>
      </div>
    </div>
  </GameBaseView>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const tanks = ref({})

function move(dx, dy) {
    stomp.publish(APP.GAME_ACTION, { action: 'MOVE', dx, dy })
}
function stop() { stomp.publish(APP.GAME_STOP, {}) }

roomStore.$subscribe((mutation, state) => {
    const gameState = state.lastGameState
    if (gameState?.type !== 'TANK_TROUBLE') return
    tanks.value = gameState.tanks
})
</script>
