<template>
  <GameBaseView title="🕵️ 谁是卧底" @stop="stop">
    <div class="spy-game">
      <div v-if="phase === 'START'" class="info">
        <el-alert title="游戏开始" type="info" :closable="false">你的词是: {{ myWord }}</el-alert>
        <el-button type="primary" @click="toVoting">进入投票阶段</el-button>
      </div>

      <div v-if="phase === 'VOTING'" class="voting">
        <h3>请投票选出卧底：</h3>
        <el-select v-model="voteTarget" placeholder="选择玩家">
          <el-option v-for="p in players" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" @click="submitVote">投票</el-button>
      </div>

      <div v-if="phase === 'RESULT'" class="result">
        <h3>游戏结束</h3>
        <p>卧底 ID: {{ spyId }}</p>
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
const phase = ref('WAITING')
const myWord = ref('')
const spyId = ref('')
const voteTarget = ref('')

const players = computed(() => roomStore.players || [])
const myPlayerId = computed(() => roomStore.deviceId)

function toVoting() {
    stomp.publish(APP.GAME_ACTION, { action: 'TO_VOTING' })
}

function submitVote() {
    if(!voteTarget.value) return
    stomp.publish(APP.GAME_ACTION, { action: 'VOTE', targetId: voteTarget.value })
}
function stop() { stomp.publish(APP.GAME_STOP, {}) }

// 监听游戏状态变化
roomStore.$subscribe((mutation, state) => {
    const gameState = state.lastGameState
    if (gameState?.type !== 'SPY') return

    phase.value = gameState.stage
    spyId.value = gameState.spyId

    // 设置我的词
    if (myPlayerId.value === gameState.spyId) {
        myWord.value = gameState.spyWord
    } else {
        myWord.value = gameState.civilianWord
    }
})
</script>
