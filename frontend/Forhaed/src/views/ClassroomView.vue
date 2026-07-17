<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStudyStore } from '@/stores/study'
import SpeakerCard from '@/components/SpeakerCard.vue'
import Avatar from '@/components/Avatar.vue'
import AnswerBox from '@/components/AnswerBox.vue'
import NodeResult from '@/components/NodeResult.vue'
import AskBox from '@/components/AskBox.vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import SidePanel from '@/components/SidePanel.vue'

const route = useRoute()
const router = useRouter()
const store = useStudyStore()

// 老师姿态：讲课/作业说明 → explain；课堂提问等待 → idle；答对 → explain；答错 → correct（纠错）
const teacherPose = computed<'idle' | 'explain' | 'correct'>(() => {
  const node = store.currentNode
  if (!node) return 'explain'
  if (node.type === 'checkpoint') {
    if (!store.nodeResult) return 'idle'
    return (store.nodeResult.score ?? 0) >= 70 ? 'explain' : 'correct'
  }
  return 'explain'
})

// 白子姿态：等待（首帧）→ idle；答对 → happy；答错 → comfort（安慰）
const baiziPose = computed<'idle' | 'happy' | 'comfort'>(() => {
  if (!store.nodeResult) return 'idle'
  return (store.nodeResult.score ?? 0) >= 70 ? 'happy' : 'comfort'
})

onMounted(async () => {
  const sessionId = Number(route.params.sessionId)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    await router.replace('/app/courses')
    return
  }
  if (!store.session || store.session.sessionId !== sessionId || !store.script) {
    await store.loadScript(sessionId).catch((err: Error) => {
      alert(err.message)
      router.replace('/app/courses')
    })
  }
})

function handleNodeSubmit(text: string) {
  const node = store.currentNode
  if (!node) return
  store.submitNode(node.nodeId, text).catch((err: Error) => {
    alert(err.message)
  })
}

function handleClassmateSubmit(text: string) {
  const node = store.currentNode
  if (!node) return
  store.submitClassmate(node.nodeId, text).catch((err: Error) => {
    alert(err.message)
  })
}

function handleAsk(question: string) {
  const node = store.currentNode
  if (!node) return
  store.ask(node.nodeId, question).catch((err: Error) => {
    alert(err.message)
  })
}
function retryDeepReview() {
  store.retryAiReview('node').catch((err: Error) => {
    alert(err.message)
  })
}

async function goToHomework() {
  const sessionId = Number(route.params.sessionId)
  await store.loadHomework(sessionId)
  router.push(`/app/homework/${sessionId}`)
}

function typeText(type: string) {
  return ({
    lecture: '讲课',
    checkpoint: '课堂提问',
    classmate: '白子互助',
    homework_intro: '作业说明',
  })[type] ?? type
}
</script>

<template>
  <div class="container">
    <section v-if="!store.currentNode" class="study-layout">
      <main>
        <div class="lesson-card">
          <p class="empty-state">课程脚本为空。</p>
        </div>
      </main>
      <SidePanel />
    </section>

    <section v-else class="study-layout">
      <main>
        <!-- 讲课 / 作业说明 -->
        <section v-if="store.currentNode.type === 'lecture' || store.currentNode.type === 'homework_intro'" class="lesson-card">
          <div class="lesson-head">
            <div>
              <h1 class="lesson-title">{{ store.currentNode.title }}</h1>
              <p class="lesson-desc">{{ store.currentNode.knowledgePoint ?? 'AI 讲课节点' }}</p>
            </div>
            <span class="chip mint">{{ store.nodeIndex + 1 }} / {{ store.script?.nodes.length }}</span>
          </div>

          <CollapsiblePanel title="小绒老师讲课" :start-open="true" :reset-key="`${store.currentNode.nodeId}:teacher-lecture`">
            <div class="dialogue-layout">
              <SpeakerCard type="teacher" :pose="teacherPose" />
              <div class="dialogue-box">
                <div class="dialogue-meta">
                  <span class="meta-name"><Avatar type="teacher" :size="28" />小绒老师</span>
                  <span>{{ store.currentNode.type === 'homework_intro' ? '准备作业' : '讲课中' }}</span>
                </div>
                <div class="dialogue-text">{{ store.currentNode.text ?? '' }}</div>
                <div class="dialogue-tip">当前节点来自后端课程脚本。</div>
              </div>
            </div>
          </CollapsiblePanel>


          <div class="action-row">
            <button class="btn secondary" :disabled="store.nodeIndex === 0" @click="store.goToPrevNode()">上一句</button>
            <button
              v-if="store.currentNode.type === 'homework_intro'"
              class="btn"
              @click="goToHomework()"
            >进入作业</button>
            <button v-else class="btn" @click="store.goToNextNode()">继续讲</button>
          </div>
        </section>

        <!-- 课堂提问 -->

        <!-- 课堂提问 -->
        <section v-else-if="store.currentNode.type === 'checkpoint'" class="lesson-card">
          <div class="lesson-head">
            <div>
              <h1 class="lesson-title">{{ store.currentNode.title }}</h1>
              <p class="lesson-desc">{{ store.currentNode.knowledgePoint ?? '课堂提问' }}</p>
            </div>
            <span class="chip mint">{{ store.nodeIndex + 1 }} / {{ store.script?.nodes.length }}</span>
          </div>

          <CollapsiblePanel title="小绒老师提问" :start-open="true" :reset-key="`${store.currentNode.nodeId}:teacher-question`">
            <div class="dialogue-layout">
              <SpeakerCard type="teacher" :pose="teacherPose" />
              <div class="dialogue-box">
                <div class="dialogue-meta">
                  <span class="meta-name"><Avatar type="teacher" :size="28" />小绒老师 · 课堂提问</span>
                  <span>实时反馈</span>
                </div>
                <div class="dialogue-text">{{ store.currentNode.question ?? '' }}</div>
                <div class="dialogue-tip">先用自己的话回答。后端会按关键词给即时反馈。</div>
              </div>
            </div>
          </CollapsiblePanel>

          <CollapsiblePanel title="你的回答" :start-open="true" :reset-key="`${store.currentNode.nodeId}:answer`">
            <AnswerBox
              action-label="提交回答"
              :keywords="store.currentNode.answerKeywords"
              @submit="handleNodeSubmit"
            />
          </CollapsiblePanel>

          <CollapsiblePanel title="即时反馈" :start-open="false" :auto-open="!!store.nodeResult" :reset-key="`${store.currentNode.nodeId}:feedback`">
            <NodeResult :result="store.nodeResult" :deep-review="store.nodeDeepReview" :deep-review-loading="store.deepReviewLoading" @retry="retryDeepReview" />
          </CollapsiblePanel>


          <div class="action-row">
            <button class="btn secondary" @click="store.goToPrevNode()">上一句</button>
            <button class="btn" :disabled="!store.nodeResult" @click="store.goToNextNode()">继续讲</button>
          </div>
        </section>

        <!-- 白子互助 -->
        <section v-else-if="store.currentNode.type === 'classmate'" class="lesson-card">
          <div class="lesson-head">
            <div>
              <h1 class="lesson-title">{{ store.currentNode.title }}</h1>
              <p class="lesson-desc">{{ store.currentNode.knowledgePoint ?? '课间互助' }}</p>
            </div>
            <span class="chip sand">协作值 {{ store.script?.classmate?.bondValue ?? 0 }}</span>
          </div>

          <CollapsiblePanel title="白子同桌请教" :start-open="true" :reset-key="`${store.currentNode.nodeId}:baizi-question`">
            <div class="dialogue-layout">
              <SpeakerCard type="baizi" :pose="baiziPose" />
              <div class="dialogue-box">
                <div class="dialogue-meta">
                  <span class="meta-name"><Avatar type="baizi" :size="28" />白子同桌 · 请教你</span>
                  <span>课间互助</span>
                </div>
                <div class="dialogue-text">{{ store.currentNode.question ?? '' }}</div>
                <div class="dialogue-tip">讲给白子听，会倒逼你整理表达。答错也会一起复盘。</div>
              </div>
            </div>
          </CollapsiblePanel>

          <CollapsiblePanel title="讲给白子听" :start-open="true" :reset-key="`${store.currentNode.nodeId}:baizi-answer`">
            <AnswerBox
              action-label="讲给白子听"
              :keywords="store.currentNode.answerKeywords"
              @submit="handleClassmateSubmit"
            />
          </CollapsiblePanel>

          <CollapsiblePanel title="即时反馈" :start-open="false" :auto-open="!!store.nodeResult" :reset-key="`${store.currentNode.nodeId}:feedback`">
            <NodeResult :result="store.nodeResult" :deep-review="store.nodeDeepReview" :deep-review-loading="store.deepReviewLoading" @retry="retryDeepReview" />
          </CollapsiblePanel>

          <div class="action-row">
            <button class="btn secondary" @click="store.goToPrevNode()">上一句</button>
            <button class="btn" :disabled="!store.nodeResult" @click="store.goToNextNode()">继续讲</button>
          </div>
        </section>

        <section class="lesson-card ai-ask-card">
          <CollapsiblePanel title="问小绒老师 AI" :start-open="true" :reset-key="`${store.currentNode.nodeId}:ask-ai`">
            <AskBox
              :messages="store.currentAskHistory"
              :loading="store.askStreaming"
              :connection-status="store.askConnectionStatus"
              @ask="handleAsk"
            />
          </CollapsiblePanel>
        </section>
      </main>

      <SidePanel />
    </section>
  </div>
</template>

