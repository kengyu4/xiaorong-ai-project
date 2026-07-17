<script setup lang="ts">
import { computed } from 'vue'
import type { AiReviewStatusResponse, NodeResult } from '@/api/types'

const props = defineProps<{ result: NodeResult | null; deepReview?: AiReviewStatusResponse | null; deepReviewLoading?: boolean }>()
const emit = defineEmits<{ retry: [] }>()
const mainReply = computed(() => { const result = props.result; if (!result) return ''; return 'teacherReply' in result ? result.teacherReply || result.feedback : result.classmateReply })
const supplement = computed(() => { const result = props.result; if (!result) return ''; return 'teacherSupplement' in result ? result.teacherSupplement : result.feedback })
const reviewStatus = computed(() => props.deepReview?.status || (props.deepReviewLoading ? 'pending' : 'pending'))
const canRetry = computed(() => reviewStatus.value === 'failed' || reviewStatus.value === 'timeout')
const runtimeText = computed(() => {
  const review = props.deepReview
  if (!review) return ''
  if (review.degraded || !review.providerCode || review.providerCode.toLowerCase() === 'mock') return `演示 / 降级讲评${review.model ? ` · ${review.model}` : ''}`
  return `真实 AI · ${review.providerCode} · ${review.model || '默认模型'}`
})
</script>

<template>
  <div v-if="result" class="result-box">
    <div class="dialogue-meta"><span>固定评分反馈</span><span class="score">{{ result.score ?? 0 }}</span></div>
    <p>{{ mainReply }}</p>
    <p v-if="supplement">{{ supplement }}</p>
    <div class="keyword-row">
      <span v-for="kw in (result.hitKeywords ?? [])" :key="kw" class="keyword hit">{{ kw }}</span>
      <span v-for="kw in (result.missKeywords ?? [])" :key="kw" class="keyword">{{ kw }}</span>
    </div>

    <div v-if="'needAiReview' in result && result.needAiReview" class="panel ai-review-panel">
      <div class="dialogue-meta">
        <strong>小绒老师 AI 深度讲评</strong>
        <span class="chip" :class="reviewStatus === 'completed' ? 'mint' : reviewStatus === 'pending' ? '' : 'sand'">
          {{ reviewStatus }}
        </span>
      </div>
      <p v-if="reviewStatus === 'pending'">正在结合你的答案生成深度讲评，最长等待 60 秒…</p>
      <p v-else-if="reviewStatus === 'completed'">{{ deepReview?.content || '深度讲评已完成。' }}</p>
      <p v-else-if="reviewStatus === 'failed'">{{ deepReview?.content || '深度讲评生成失败。' }}</p>
      <p v-else-if="reviewStatus === 'timeout'">等待已超过 60 秒，你可以重新获取。</p>
      <div v-if="deepReview && reviewStatus !== 'pending'" class="keyword-row">
        <span class="chip" :class="deepReview.degraded ? 'sand' : 'mint'">{{ runtimeText }}</span>
      </div>
      <button v-if="canRetry" class="btn secondary" :disabled="deepReviewLoading" @click="emit('retry')">
        {{ deepReviewLoading ? '重新获取中…' : '重新获取' }}
      </button>
    </div>
  </div>
</template>
