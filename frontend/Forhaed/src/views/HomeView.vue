<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStudyStore } from '@/stores/study'
import CourseCard from '@/components/CourseCard.vue'

const store = useStudyStore()
const router = useRouter()
const firstCourseId = computed(() => store.courses.find((course) => course.lessonCount > 0)?.courseId)

onMounted(() => {
  if (!store.courses.length) store.loadCourses()
  store.loadOverview().catch(() => undefined)
  store.loadOverviewAdvice().catch(() => undefined)
})

function refreshAdvice() {
  store.loadOverviewAdvice().catch(() => undefined)
}

watch(() => store.session, (session) => {
  if (session) {
    router.push(`/app/classroom/${session.sessionId}`)
  }
})

function startSession(courseId: number) {
  if (!courseId) return
  store.startSession(courseId).catch((err: Error) => {
    alert(err.message)
  })
}
</script>

<template>
  <div class="container">
    <section class="hero">
      <div class="hero-copy">
        <div>
          <div class="eyebrow">Stitch Cozy Tech × 本地角色素材</div>
          <h1 class="hero-title">把题库变成会讲课、会提问、会复盘的 AI 课堂</h1>
          <p class="hero-desc">小绒老师先讲知识点，白子同桌在课间向你请教。答完后直接进入作业，提交一题就讲评一题。</p>
        </div>
        <div class="hero-actions">
          <button class="btn" :disabled="!firstCourseId || store.loading" @click="startSession(firstCourseId ?? 0)">开始学习</button>
        </div>
      </div>

      <div class="hero-visual">
        <div class="visual-top">
          <span class="chip">首页 P0 骨架</span>
          <span class="chip mint">可连后端</span>
        </div>
        <div class="character-stage">
          <div class="character-card">
            <img src="/src/assets/characters/teacher-explain.png" alt="小绒老师">
            <div class="character-label">
              <strong>小绒老师</strong>
              <span>讲课、提问、纠错、作业讲评</span>
            </div>
          </div>
          <div class="character-card baizi">
            <img src="/src/assets/characters/baizi-ask.png" alt="白子同桌">
            <div class="character-label">
              <strong>白子同桌</strong>
              <span>请教你、鼓励你、一起复盘</span>
            </div>
          </div>
        </div>
        <div class="mini-dialogs">
          <p>小绒老师：先选一个专题，我会把题目拆成几个容易理解的小节点。</p>
          <p>白子：我会在沉浸模式里向你请教问题，我们一起把知识点讲清楚。</p>
        </div>
      </div>
    </section>

    <section class="stats-grid">
      <div class="stat-card">
        <span>课程数量</span>
        <strong>{{ store.courses.length || '--' }}</strong>
      </div>
      <div class="stat-card">
        <span>今日进度</span>
        <strong>{{ store.overview?.completedCount ?? 0 }}</strong>
      </div>
      <div class="stat-card">
        <span>薄弱点</span>
        <strong>{{ store.overview?.topWeakTag || '暂无' }}</strong>
        <small v-if="store.overview?.weakTagCount">{{ store.overview.weakTagCount }} 个待巩固标签</small>
      </div>
      <div class="stat-card">
        <span>协作值</span>
        <strong>{{ store.session?.mode === 'immersive' ? '12' : '--' }}</strong>
      </div>
    </section>

    <section class="section advice-section">
      <div class="section-head">
        <div>
          <h2>小绒老师的学习建议</h2>
          <p>根据你的薄弱标签、历史得分和最近课程生成 2–3 条建议。</p>
        </div>
        <button class="btn secondary" :disabled="store.overviewAdviceLoading" @click="refreshAdvice">
          {{ store.overviewAdviceLoading ? '刷新中…' : '刷新建议' }}
        </button>
      </div>

      <div v-if="store.overviewAdviceLoading && !store.overviewAdvice" class="panel">
        <p class="empty-state">正在整理你的学习记录…</p>
      </div>
      <div v-else-if="store.overviewAdviceError && !store.overviewAdvice" class="panel">
        <p class="empty-state">{{ store.overviewAdviceError }}</p>
        <button class="btn" @click="refreshAdvice">重新加载</button>
      </div>
      <div v-else-if="store.overviewAdvice" class="panel advice-panel">
        <div class="dialogue-meta">
          <span>{{ store.overviewAdvice.teacherSummary }}</span>
          <span class="chip" :class="store.overviewAdvice.degraded ? 'sand' : 'mint'">
            {{ store.overviewAdvice.degraded || store.overviewAdvice.providerCode === 'mock'
              ? '演示 / 降级建议'
              : `真实 AI · ${store.overviewAdvice.providerCode} · ${store.overviewAdvice.model}` }}
          </span>
        </div>
        <p v-if="!store.overviewAdvice.hasLearningData" class="empty-state">
          还没有学习数据。完成一次课堂或作业后，我会给出更有针对性的建议。
        </p>
        <div v-else class="record-list">
          <div v-for="(item, index) in store.overviewAdvice?.suggestions.slice(0, 3)" :key="index" class="record-item">
            <strong>建议 {{ index + 1 }}</strong>
            <p>{{ item }}</p>
          </div>
        </div>
        <div v-if="store.overviewAdvice.weakTags.length" class="keyword-row">
          <span v-for="tag in store.overviewAdvice.weakTags" :key="tag" class="tag">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <div>
          <h2>选择学习模式</h2>
          <p>快速模式少互动；沉浸模式会启用白子同桌和协作值。</p>
        </div>
      </div>
      <div class="mode-grid">
        <button class="mode-card active">
          <div class="mode-icon">伴</div>
          <div class="mode-copy">
            <strong>沉浸模式</strong>
            <span>小绒老师讲课，白子同桌一起复盘。</span>
          </div>
        </button>
        <button class="mode-card">
          <div class="mode-icon">快</div>
          <div class="mode-copy">
            <strong>快速模式</strong>
            <span>少互动，直接刷重点，适合复习。</span>
          </div>
        </button>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <div>
          <h2>推荐课程</h2>
          <p>点击开始学习会调用后端创建 session，然后拉取课程脚本。</p>
        </div>
        <span class="chip">GET /api/study/courses</span>
      </div>

      <div v-if="store.loading" class="panel">
        <p class="empty-state">课程加载中...</p>
      </div>
      <div v-else-if="store.error" class="panel">
        <p class="empty-state">{{ store.error }}</p>
        <button class="btn" @click="store.loadCourses()">重新加载</button>
      </div>
      <div v-else-if="!store.courses.length" class="panel">
        <p class="empty-state">暂时没有可学习课程。</p>
      </div>
      <div v-else class="course-grid">
        <CourseCard
          v-for="(course, index) in store.courses"
          :key="course.courseId"
          :course="course"
          :index="index"
          :disabled="course.lessonCount === 0 || !!store.startingCourseId"
          :loading="store.startingCourseId === course.courseId"
          @start="startSession"
        />
      </div>
    </section>

    <section class="section persona-grid">
      <div class="persona-card">
        <div class="avatar-mini">
          <img src="/src/assets/characters/teacher-explain.png" alt="小绒老师">
        </div>
        <div>
          <strong>小绒老师</strong>
          <p>课堂节点来自后端脚本，讲完后会进入作业。</p>
        </div>
      </div>
      <div class="persona-card">
        <div class="avatar-mini">
          <img src="/src/assets/characters/baizi-happy.png" alt="白子同桌">
        </div>
        <div>
          <strong>白子同桌</strong>
          <p>沉浸模式下会出现同桌互助，答对会增加协作值。</p>
        </div>
      </div>
    </section>
  </div>
</template>

