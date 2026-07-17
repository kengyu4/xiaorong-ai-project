<script setup lang="ts">
import { computed } from 'vue'
import teacherIdle from '@/assets/characters/teacher-idle.png'
import teacherExplain from '@/assets/characters/teacher-explain.png'
import teacherCorrect from '@/assets/characters/teacher-correct.png'
import baiziIdle from '@/assets/characters/baizi-idle.png'
import baiziAsk from '@/assets/characters/baizi-ask.png'
import baiziHappy from '@/assets/characters/baizi-happy.png'
import baiziComfort from '@/assets/characters/baizi-comfort.png'

type TeacherPose = 'idle' | 'explain' | 'correct'
type BaiziPose = 'idle' | 'ask' | 'happy' | 'comfort'

const props = defineProps<{
  type: 'teacher' | 'baizi'
  pose?: TeacherPose | BaiziPose
}>()

const teacherAssets: Record<TeacherPose, string> = {
  idle: teacherIdle,
  explain: teacherExplain,
  correct: teacherCorrect,
}

const baiziAssets: Record<BaiziPose, string> = {
  idle: baiziIdle,
  ask: baiziAsk,
  happy: baiziHappy,
  comfort: baiziComfort,
}

const labelMap = {
  teacher: { name: '小绒老师', role: 'AI 老师' },
  baizi: { name: '白子同桌', role: '学习伙伴' },
}

const label = computed(() => labelMap[props.type])
const image = computed(() => {
  if (props.type === 'teacher') {
    const pose = (props.pose ?? 'explain') as TeacherPose
    return teacherAssets[pose] ?? teacherExplain
  }
  const pose = (props.pose ?? 'idle') as BaiziPose
  return baiziAssets[pose] ?? baiziIdle
})
</script>

<template>
  <div class="speaker-card" :class="type === 'baizi' ? 'baizi' : ''">
    <img :src="image" :alt="label.name">
    <div class="speaker-name">
      <strong>{{ label.name }}</strong>
      <span>{{ label.role }}</span>
    </div>
  </div>
</template>
