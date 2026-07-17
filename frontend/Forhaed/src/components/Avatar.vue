<script setup lang="ts">
import { computed } from 'vue'
import teacherAvatar from '@/assets/characters/teacher-avatar.png'
import baiziAvatar from '@/assets/characters/baizi-avatar.png'

const props = defineProps<{
  type: 'teacher' | 'baizi'
  size?: number
}>()

const avatarMap = {
  teacher: { src: teacherAvatar, alt: '小绒老师' },
  baizi: { src: baiziAvatar, alt: '白子同桌' },
}

const avatar = computed(() => avatarMap[props.type])
const sizePx = computed(() => `${props.size ?? 40}px`)
</script>

<template>
  <span class="avatar" :class="type" :style="{ width: sizePx, height: sizePx }">
    <img :src="avatar.src" :alt="avatar.alt">
  </span>
</template>

<style scoped>
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
  background: #eef6fb;
  border: 2px solid #ffffff;
  box-shadow: var(--shadow-soft, 0 4px 10px rgba(72, 97, 116, 0.14));
}

.avatar.baizi {
  background: #f4f0fb;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 抠像后人物四周有透明留白，放大并上移让面部填满圆形 */
  transform: scale(1.32) translateY(6%);
}
</style>
