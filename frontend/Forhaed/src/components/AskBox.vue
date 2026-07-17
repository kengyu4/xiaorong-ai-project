<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AskMessage } from '@/api/types'

type ConnectionStatus = 'idle' | 'connecting' | 'streaming' | 'completed' | 'fallback' | 'failed'

const props = withDefaults(defineProps<{
  messages?: AskMessage[]
  loading?: boolean
  connectionStatus?: ConnectionStatus
}>(), {
  messages: () => [],
  loading: false,
  connectionStatus: 'idle',
})
const emit = defineEmits<{ ask: [question: string] }>()
const question = ref('')
const quickQuestions = ['能举个实际例子吗？', '面试时应该怎么表达？', '它和相近概念有什么区别？']

const statusText = computed(() => ({
  idle: '可随时自由提问',
  connecting: '正在连接小绒老师 AI…',
  streaming: '正在接收增量回答…',
  completed: '本轮回答完成',
  fallback: '流式连接失败，已使用普通请求完成',
  failed: '连接失败，可以重新提问',
})[props.connectionStatus])

function handleAsk(text = question.value) {
  const value = text.trim()
  if (!value || props.loading) return
  question.value = ''
  emit('ask', value)
}

function runtimeText(message: AskMessage) {
  if (message.degraded || !message.providerCode || message.providerCode.toLowerCase() === 'mock') {
    return `演示 / 降级回答${message.model ? ` · ${message.model}` : ''}`
  }
  return `真实 AI · ${message.providerCode} · ${message.model || '默认模型'}`
}
</script>

<template>
  <div class="ask-box">
    <div class="ask-heading">
      <div>
        <strong>问小绒老师 AI</strong>
        <p>可输入任意问题；快捷问题只是可选提示。</p>
      </div>
      <span class="connection-status" :class="connectionStatus">{{ statusText }}</span>
    </div>

    <div v-if="messages.length" class="ask-history" aria-live="polite">
      <article v-for="message in messages" :key="message.id" class="ask-message" :class="message.role">
        <strong>{{ message.role === 'user' ? '你' : '小绒老师 AI' }}</strong>
        <p>{{ message.content }}<span v-if="message.streaming" class="stream-cursor">▍</span></p>
        <div v-if="message.role === 'assistant' && !message.streaming" class="runtime-meta">
          <span class="chip" :class="message.degraded ? 'sand' : 'mint'">{{ runtimeText(message) }}</span>
          <span v-if="message.fallback" class="chip sand">普通 POST 降级链路</span>
        </div>
      </article>
    </div>
    <p v-else class="empty-state">还没有对话。你可以追问当前知识点，也可以问一个全新的问题。</p>

    <div class="keyword-row">
      <button v-for="item in quickQuestions" :key="item" class="tag" :disabled="loading" @click="handleAsk(item)">{{ item }}</button>
    </div>
    <div class="action-row">
      <input v-model="question" :disabled="loading" placeholder="输入你真正想问的问题…" @keyup.enter="handleAsk()">
      <button class="btn warn" :disabled="loading || !question.trim()" @click="handleAsk()">{{ loading ? '回答中…' : '发送问题' }}</button>
    </div>
  </div>
</template>

<style scoped>
.ask-heading,
.runtime-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.ask-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.connection-status {
  border-radius: 999px;
  padding: 6px 10px;
  background: #eef3f5;
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.connection-status.connecting,
.connection-status.streaming {
  background: #ecfeff;
  color: #0f766e;
}

.connection-status.fallback,
.connection-status.failed {
  background: #fff7ed;
  color: #c2410c;
}

.ask-history {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

.ask-message {
  max-width: 88%;
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 12px 14px;
  background: #fff;
}

.ask-message.user {
  justify-self: end;
  background: #edf6fb;
  border-color: #cbe6fc;
}

.ask-message.assistant {
  justify-self: start;
  background: #fffdf8;
  border-color: #fed7aa;
}

.ask-message p {
  margin: 6px 0 0;
  color: #334155;
  line-height: 1.75;
  white-space: pre-wrap;
}

.runtime-meta {
  justify-content: flex-start;
  margin-top: 10px;
}

.stream-cursor {
  color: var(--primary);
  animation: blink 0.8s steps(1) infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
