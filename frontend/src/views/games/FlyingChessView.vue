<template>
  <GameBaseView title="✈️ 飞行棋" @stop="stop">
    <div class="game-container">
      <div class="board">
        <div v-for="(pos, id) in positions" :key="id" class="player-pos">
          玩家 {{ id.substring(0,4) }}: 格子 {{ pos }}
        </div>
      </div>
      <div class="controls">
        <el-button type="primary" @click="rollDice">掷骰子</el-button>
      </div>
    </div>
  </GameBaseView>
</template>

<script setup>
import { ref } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const positions = ref({})

function rollDice() { stomp.publish(APP.GAME_ACTION, { action: 'ROLL' }) }
function stop() { stomp.publish(APP.GAME_STOP, {}) }

roomStore.$subscribe((mutation, state) => {
    const gameState = state.lastGameState
    if (gameState?.type !== 'FLYING_CHESS') return
    positions.value = gameState.positions
})
</script>
