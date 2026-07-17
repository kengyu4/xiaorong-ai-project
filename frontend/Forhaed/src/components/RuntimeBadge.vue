<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getRuntimeStatus } from '@/api/admin'
import type { RuntimeStatus } from '@/api/types'

const status = ref<RuntimeStatus | null>(null)
const failed = ref(false)

const label = computed(() => {
  if (failed.value) return '后端离线'
  if (!status.value) return '检测中'
  return status.value.aiRealEnabled ? status.value.defaultProviderCode : 'mock'
})

const className = computed(() => {
  if (failed.value) return 'chip danger'
  if (!status.value) return 'chip'
  return status.value.aiRealEnabled ? 'chip mint' : 'chip warn'
})

onMounted(async () => {
  try {
    status.value = await getRuntimeStatus()
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <RouterLink :class="className" to="/admin/runtime">
    {{ label }}
  </RouterLink>
</template>
