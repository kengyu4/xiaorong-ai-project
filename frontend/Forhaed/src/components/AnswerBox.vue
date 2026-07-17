<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  actionLabel?: string
  placeholder?: string
  keywords?: string[]
}>()

const emit = defineEmits<{
  submit: [text: string]
}>()

const text = ref('')

function handleSubmit() {
  const value = text.value.trim()
  if (!value) return
  emit('submit', value)
}
</script>

<template>
  <div class="answer-box">
    <strong>你的回答</strong>
    <textarea
      v-model="text"
      :placeholder="placeholder ?? '写下你的理解，提交后会得到即时反馈。'"
    />
    <div v-if="keywords?.length" class="keyword-row">
      <span v-for="kw in keywords" :key="kw" class="keyword">{{ kw }}</span>
    </div>
    <div class="action-row">
      <button class="btn" @click="handleSubmit">
        {{ actionLabel ?? '提交回答' }}
      </button>
    </div>
  </div>
</template>
