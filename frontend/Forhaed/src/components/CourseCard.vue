<script setup lang="ts">
import type { Course } from '@/api/types'

defineProps<{
  course: Course
  index: number
  disabled?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  start: [courseId: number]
}>()

function difficultyText(value: string) {
  return ({ easy: '入门', medium: '进阶', hard: '挑战' })[value] ?? value ?? '入门'
}
</script>

<template>
  <article class="course-card">
    <div class="course-cover">
      <span class="chip">{{ difficultyText(course.difficulty) }}</span>
      <div class="cover-icon">{{ index + 1 }}</div>
    </div>
    <div>
      <h3>{{ course.title }}</h3>
      <p>{{ course.description }}</p>
      <div class="course-meta">
        <span v-for="tag in (course.tags ?? []).slice(0, 3)" :key="tag" class="tag" :class="tag === (course.tags ?? [])[1] ? 'mint' : tag === (course.tags ?? [])[2] ? 'sand' : ''">{{ tag }}</span>
        <span class="pill">{{ course.lessonCount }} 节点</span>
        <span class="pill">{{ course.homeworkCount }} 题</span>
      </div>
    </div>
    <button
      class="btn"
      :disabled="disabled || loading"
      @click="emit('start', course.courseId)"
    >
      {{ loading ? '创建中...' : course.lessonCount === 0 ? '即将开放' : '开始学习' }}
    </button>
  </article>
</template>
