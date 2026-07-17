<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStudyStore } from '@/stores/study'

const store = useStudyStore()
const router = useRouter()
const route = useRoute()

onMounted(async () => {
  const sessionId = Number(route.params.sessionId)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    await router.replace('/app/courses')
    return
  }
  if (!store.review || store.session?.sessionId !== sessionId) {
    await store.loadReview(sessionId).catch((err: Error) => {
      alert(err.message)
      router.replace('/app/courses')
    })
  }
})

function restart() {
  if (!store.session || !store.session.courseId) {
    router.push('/app/courses')
    return
  }
  store.startSession(store.session.courseId).then(() => {
    router.push(`/app/classroom/${store.session?.sessionId}`)
  })
}
</script>

<template>
  <div class="container">
    <section v-if="!store.review" class="lesson-card">
      <p class="empty-state">暂无复盘数据。</p>
    </section>

    <section v-else class="lesson-card">
      <div class="lesson-head">
        <div>
          <h1 class="lesson-title">本轮学习复盘</h1>
          <p class="lesson-desc">{{ store.review.summary }}</p>
        </div>
        <span class="chip mint">均分 {{ store.review.averageScore }}</span>
      </div>

      <div class="runtime-banner" :class="store.review.degraded ? 'degraded' : 'real'">
        <strong>{{ store.review.degraded ? '演示 / 降级复盘' : '真实 AI 复盘' }}</strong>
        <span>Provider：{{ store.review.providerCode || 'mock' }} · 模型：{{ store.review.model || '演示模型' }}</span>
      </div>

      <div class="review-grid">
        <article class="course-card">
          <div class="course-cover">
            <span class="chip mint">掌握度</span>
            <div class="cover-icon">{{ store.review.averageScore }}</div>
          </div>
          <div>
            <h3>小绒老师总结</h3>
            <p>{{ store.review.teacherSummary }}</p>
          </div>
          <button class="btn" @click="restart()">重新学习</button>
        </article>

        <article class="course-card">
          <div class="course-cover">
            <span class="chip sand">白子</span>
            <div class="cover-icon">白</div>
          </div>
          <div>
            <h3>白子同桌</h3>
            <p>{{ store.review.classmateReply }}</p>
            <p>协作值：{{ store.review.bondValue }}</p>
          </div>
          <button class="btn ghost" @click="store.reset(); router.push('/app/courses')">返回首页</button>
        </article>

        <article class="course-card">
          <div class="course-cover">
            <span class="chip">下一课</span>
            <div class="cover-icon">下</div>
          </div>
          <div>
            <h3>{{ store.review.nextCourse?.title ?? '下一节推荐' }}</h3>
            <p>薄弱点：{{ (store.review.weakTags ?? []).join('、') }}</p>
          </div>
          <button class="btn secondary" @click="store.reset(); router.push('/app/courses')">选择课程</button>
        </article>
      </div>

      <div class="panel">
        <h2>下一步建议</h2>
        <div class="record-list">
          <div
            v-for="(item, index) in (store.review.nextActions ?? [])"
            :key="index"
            class="record-item"
          >{{ item }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.runtime-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid #a7f3d0;
  border-radius: 16px;
  color: #166534;
  background: #f0fdf4;
}

.runtime-banner.degraded {
  border-color: #fed7aa;
  color: #c2410c;
  background: #fff7ed;
}

.runtime-banner span {
  color: inherit;
  font-size: 13px;
}

@media (max-width: 640px) {
  .runtime-banner { align-items: flex-start; flex-direction: column; }
}
</style>
