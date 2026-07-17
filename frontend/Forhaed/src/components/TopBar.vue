<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useStudyStore } from '@/stores/study'

defineProps<{
  subtitle?: string
}>()

const store = useStudyStore()
const router = useRouter()

function goHome() {
  store.reset()
  router.push('/')
}
</script>

<template>
  <header class="topbar">
    <div class="brand">
      <button class="brand-mark" @click="goHome">AI</button>
      <div class="brand-copy">
        <strong>小绒老师助教</strong>
        <span>{{ subtitle ?? 'AI 智能刷题助手' }}</span>
      </div>
    </div>
    <div class="top-actions">
      <span class="chip mint">后端 8088</span>
      <span class="chip sand">{{ store.session?.mode === 'immersive' ? '沉浸模式' : '快速模式' }}</span>
      <button v-if="store.isInSession" class="btn secondary" @click="goHome">回到首页</button>
      <button v-else class="btn secondary" @click="store.loadCourses()">重新加载</button>
    </div>
  </header>
</template>
