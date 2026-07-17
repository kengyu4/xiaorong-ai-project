<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  startOpen?: boolean
  autoOpen?: boolean
  resetKey?: string | number
}>(), {
  startOpen: true,
  autoOpen: false,
})

const open = ref(isDefaultOpen())

function isDefaultOpen() {
  return props.startOpen
}

watch(() => props.autoOpen, (val) => {
  if (val) open.value = true
})

watch(
  () => [props.resetKey, props.startOpen] as const,
  () => {
    open.value = isDefaultOpen()
  },
)

function toggle() {
  open.value = !open.value
}
</script>

<template>
  <div>
    <button class="coll-header" @click="toggle">
      <span>{{ title }}</span>
      <span class="coll-arrow" :class="{ open }">{{ open ? '▾' : '▸' }}</span>
    </button>
    <div v-show="open" class="coll-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.coll-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0;
  border: none;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--primary-strong);
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  user-select: none;
}

.coll-header:hover {
  color: var(--primary);
}

.coll-arrow {
  color: var(--muted);
  font-size: 18px;
  transition: transform 0.2s;
}

.coll-body {
  padding: 14px 0 0;
}
</style>
