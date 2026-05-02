<template>
  <div class="player-chip-wrapper">
    <UserListItem :user="player" :me="me" :clickable="true" @click="onClick" />
  </div>
</template>

<script setup>
import { useUserDetailStore } from '../stores/userDetail'
import UserListItem from './UserListItem.vue'

const props = defineProps({
  player: { type: Object, required: true },
  me: { type: Boolean, default: false }
})

const detail = useUserDetailStore()

function onClick(user) {
  detail.open(user.id)
}
</script>

<style scoped>
.player-chip-wrapper { display: inline-flex; }
</style>
