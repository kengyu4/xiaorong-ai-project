<script setup lang="ts">
defineProps<{
  name: string
  role: string
  image: string
  tone?: 'teacher' | 'baizi'
}>()
</script>

<template>
  <section class="character-panel" :class="tone || 'teacher'">
    <img :src="image" :alt="name" />
    <div class="character-name">
      <strong>{{ name }}</strong>
      <span>{{ role }}</span>
    </div>
  </section>
</template>

<style scoped>
.character-panel {
  position: relative;
  min-height: 306px;
  overflow: hidden;
  border: 1px solid rgba(203, 214, 222, 0.8);
  border-radius: 30px;
  background: linear-gradient(180deg, #fff, #edf7fb);
  box-shadow: var(--shadow-soft);
  padding: 16px;
  display: grid;
  align-content: end;
}

.character-panel.baizi {
  background: linear-gradient(180deg, #fff, #f8f0ee);
}

.character-panel img {
  position: absolute;
  left: 50%;
  top: 12px;
  width: min(96%, 260px);
  height: 226px;
  object-fit: contain;
  transform: translateX(-50%);
  filter: drop-shadow(0 20px 18px rgba(72, 97, 116, 0.13));
}

.character-name {
  position: relative;
  z-index: 2;
  display: grid;
  gap: 4px;
}

.character-name strong {
  color: var(--primary-strong);
  font-size: 17px;
}

.character-name span {
  color: var(--muted);
  font-size: 12px;
}
</style>
