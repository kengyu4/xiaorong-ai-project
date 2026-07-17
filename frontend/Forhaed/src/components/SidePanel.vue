<script setup lang="ts">
import { ref, computed } from 'vue'
import { useStudyStore } from '@/stores/study'
import Avatar from '@/components/Avatar.vue'

const store = useStudyStore()

const pageSize = 5
const currentPage = ref(1)
const nodes = computed(() => store.script?.nodes ?? [])
const totalPages = computed(() => Math.max(1, Math.ceil(nodes.value.length / pageSize)))
const pagedNodes = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return nodes.value.slice(start, start + pageSize)
})
const globalIndex = computed(() => (i: number) => (currentPage.value - 1) * pageSize + i + 1)

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}
</script>

<template>
  <aside class="side-panel">
    <section class="panel">
      <h2>课堂地图</h2>
      <div class="timeline">
        <div
          v-for="(node, i) in pagedNodes"
          :key="node.nodeId"
          class="timeline-item"
          :class="{ active: globalIndex(i) - 1 === store.nodeIndex && !store.isInHomework }"
        >
          <strong>{{ globalIndex(i) }}. {{ node.title }}</strong><br>
          {{ { lecture: '讲课', checkpoint: '课堂提问', classmate: '白子互助', homework_intro: '作业说明' }[node.type] ?? node.type }}
        </div>
      </div>
      <div v-if="totalPages > 1" class="pagination">
        <button class="page-btn" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">‹</button>
        <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="goToPage(currentPage + 1)">›</button>
      </div>
    </section>

    <section class="panel">
      <h2>学习记录</h2>
      <div class="record-list">
        <div
          v-for="(record, index) in store.records.slice(-6)"
          :key="index"
          class="record-item"
        >
          {{ record }}
        </div>
        <div v-if="!store.records.length" class="record-item">还没有记录。</div>
      </div>
    </section>

    <section class="panel">
      <h2>角色状态</h2>
      <div class="persona-card">
        <Avatar type="baizi" :size="72" />
        <div>
          <strong>白子同桌</strong>
          <p>协作值 {{ store.script?.classmate?.bondValue ?? store.review?.bondValue ?? 0 }}</p>
        </div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
}
.page-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(223, 228, 232, 0.92);
  background: #f8fafb;
  color: #536170;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.page-btn:hover:not(:disabled) {
  background: #edf6fb;
  border-color: #cbe6fc;
}
.page-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.page-info {
  font-size: 13px;
  color: #536170;
  min-width: 48px;
  text-align: center;
}
</style>
