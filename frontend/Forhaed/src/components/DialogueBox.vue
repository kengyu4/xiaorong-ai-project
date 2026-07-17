<script setup lang="ts">
defineProps<{
  title: string
  meta: string
  text: string
  tip?: string
}>()
</script>

<template>
  <section class="dialogue-box">
    <div class="dialogue-meta">
      <span>{{ title }}</span>
      <span>{{ meta }}</span>
    </div>
    <div class="dialogue-text">{{ text }}</div>
    <div v-if="tip" class="dialogue-tip">{{ tip }}</div>
  </section>
</template>

<style scoped>
.dialogue-box {
  min-height: 306px;
  border: 1px solid rgba(158, 207, 209, 0.64);
  border-radius: 28px 28px 28px 12px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
  padding: 22px;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 14px;
}

.dialogue-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--primary-strong);
  font-size: 13px;
  font-weight: 900;
}

.dialogue-meta span:last-child {
  color: var(--muted);
}

.dialogue-text {
  color: #25323b;
  font-size: 19px;
  line-height: 1.85;
  white-space: pre-line;
}

.dialogue-tip {
  border-top: 1px dashed #ccd6dd;
  padding-top: 12px;
  color: #536170;
  line-height: 1.6;
  font-size: 13px;
}

@media (max-width: 760px) {
  .dialogue-text {
    font-size: 17px;
  }
}
</style>
