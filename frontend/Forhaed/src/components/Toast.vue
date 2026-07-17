<script setup lang="ts">
import { ref, onMounted } from 'vue'

const message = ref('')
let timer: ReturnType<typeof setTimeout> | null = null

function show(msg: string) {
  message.value = msg
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    message.value = ''
  }, 2200)
}

onMounted(() => {
  window.addEventListener('app-toast', ((e: CustomEvent) => {
    show(e.detail)
  }) as EventListener)
})

function toast(msg: string) {
  show(msg)
}

defineExpose({ toast })
</script>

<template>
  <Teleport to="body">
    <div v-if="message" class="toast">{{ message }}</div>
  </Teleport>
</template>
