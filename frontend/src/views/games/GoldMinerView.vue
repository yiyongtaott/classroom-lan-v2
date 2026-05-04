<template>
  <GameBaseView title="💰 黄金矿工" @stop="stop">
    <div class="game-container">
      <el-statistic title="得分" :value="score" />
      <el-button type="primary" @click="grab" size="large">抓取</el-button>
    </div>
  </GameBaseView>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useStomp } from '../../composables/useStomp'
import { useRoomStore } from '../../stores/room'
import { APP } from '../../appConfig'
import GameBaseView from '../../components/GameBaseView.vue'

const stomp = useStomp()
const roomStore = useRoomStore()
const score = ref(0)

function grab() { stomp.publish(APP.GAME_ACTION, { action: 'GRAB' }) }
function stop() { stomp.publish(APP.GAME_STOP, {}) }

watch(() => roomStore.lastGameState, (state) => {
  if (state?.gameType === 'GOLD_MINER') score.value = state.data.score
})
</script>
