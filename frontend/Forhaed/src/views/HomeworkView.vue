<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStudyStore } from '@/stores/study'
import SidePanel from '@/components/SidePanel.vue'

const store = useStudyStore()
const router = useRouter()
const route = useRoute()
const answerText = ref('')
const currentHomework = computed(() => store.activeHomework)

onMounted(async () => {
  const sessionId = Number(route.params.sessionId)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    await router.replace('/app/courses')
    return
  }
  if (!store.homework.length || store.session?.sessionId !== sessionId) {
    await store.loadHomework(sessionId).catch((err: Error) => {
      alert(err.message)
      router.replace('/app/courses')
    })
  }
})

function difficultyText(value: string) {
  return ({ easy: '入门', medium: '进阶', hard: '挑战' })[value] ?? value ?? '入门'
}

async function submitCurrent() {
  const item = currentHomework.value
  if (!item) return
  const text = answerText.value.trim()
  if (!text) return alert('先写下你的作答')

  try {
    await store.submitHomeworkItem(item.topicId, text)
    answerText.value = ''
  } catch (err) {
    alert((err as Error).message)
  }
}

function goPrev() {
  store.goToPrevHomework()
  answerText.value = ''
}

function goNext() {
  if (store.homeworkIndex >= store.homework.length - 1) {
    const sessionId = Number(route.params.sessionId)
    store.loadReview(sessionId).then(() => {
      router.push(`/app/review/${sessionId}`)
    })
  } else {
    store.goToNextHomework()
    answerText.value = ''
  }
}
</script>

<template>
  <div class="container">
    <section class="study-layout">
      <main>
        <section class="lesson-card homework-card">
          <div class="lesson-head">
            <div>
              <h1 class="lesson-title">课后作业</h1>
              <p class="lesson-desc">提交一题，小绒老师就讲评一题。</p>
            </div>
            <span class="chip mint">{{ store.homeworkIndex + 1 }} / {{ store.homework.length }}</span>
          </div>

          <template v-if="currentHomework">
            <div>
              <div class="keyword-row">
                <span
                  v-for="tag in (currentHomework.tags ?? [])"
                  :key="tag"
                  class="tag"
                >{{ tag }}</span>
                <span class="tag sand">{{ difficultyText(currentHomework.difficulty) }}</span>
              </div>
              <h2 class="question-title">{{ currentHomework.title }}</h2>
              <div class="question-body">{{ currentHomework.body }}</div>
              <div class="homework-answer">
                <textarea
                  v-model="answerText"
                  placeholder="先自己回答，再提交给小绒老师讲评。"
                />
              </div>
            </div>

            <div class="action-row">
              <button class="btn" @click="submitCurrent()">提交本题</button>
              <button
                class="btn secondary"
                :disabled="store.homeworkIndex === 0"
                @click="goPrev()"
              >上一题</button>
              <button class="btn secondary" @click="goNext()">
                {{ store.homeworkIndex === store.homework.length - 1 ? '完成复盘' : '下一题' }}
              </button>
            </div>

            <div v-if="store.homeworkResult" class="result-box">
              <div class="dialogue-meta">
                <span>小绒老师逐题讲评</span>
                <span class="score">{{ store.homeworkResult.score }}</span>
              </div>
              <p>{{ store.homeworkResult.feedback }}</p>
              <p>{{ store.homeworkResult.aiReview }}</p>
              <div class="keyword-row">
                <span
                  v-for="kw in (store.homeworkResult.hitKeywords ?? [])"
                  :key="kw"
                  class="keyword hit"
                >{{ kw }}</span>
                <span
                  v-for="kw in (store.homeworkResult.missKeywords ?? [])"
                  :key="kw"
                  class="keyword"
                >{{ kw }}</span>
              </div>
              <p><strong>参考答案：</strong>{{ store.homeworkResult.standardAnswer }}</p>
              <div v-if="store.homeworkResult.needAiReview" class="panel ai-review-panel">
                <strong>小绒老师深度讲评</strong>
                <p v-if="store.homeworkDeepReview?.content">{{ store.homeworkDeepReview.content }}</p>
                <p v-else>{{ store.deepReviewLoading ? '正在生成深度讲评…' : '深度讲评任务已提交。' }}</p>
              </div>
            </div>
          </template>

          <p v-else class="empty-state">暂无作业题。</p>
        </section>
      </main>

      <SidePanel />
    </section>
  </div>
</template>
