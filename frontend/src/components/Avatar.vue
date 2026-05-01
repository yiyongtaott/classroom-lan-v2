<template>
  <div class="avatar" :style="{ width: size + 'px', height: size + 'px', fontSize: (size / 2.4) + 'px' }">
    <img v-if="src" :src="src" alt="avatar" @error="onError" />
    <span v-else>{{ initial }}</span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  player: { type: Object, default: () => ({}) },
  size: { type: Number, default: 36 }
})

const fallback = ref(false)

const src = computed(() => {
  if (fallback.value) return null
  if (!props.player) return null
  return props.player.avatar || null
})

watch(() => props.player?.id, () => { fallback.value = false })

const initial = computed(() => {
  const n = props.player?.name || props.player?.hostname || '?'
  return n.charAt(0).toUpperCase()
})

function onError() { fallback.value = true }
</script>

<style scoped>
.avatar {
  border-radius: 50%;
  background: linear-gradient(135deg, #4ade80 0%, #16a34a 100%);
  color: white; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; flex-shrink: 0; user-select: none;
}
.avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
</style>
