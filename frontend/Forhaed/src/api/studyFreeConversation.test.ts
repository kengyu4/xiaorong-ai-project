import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const root = new URL('../', import.meta.url)

async function source(path: string) {
  return readFile(new URL(path, root), 'utf8')
}

test('课程页薄弱点来自当前用户学习概览，不再硬编码 Proxy', async () => {
  const home = await source('views/HomeView.vue')
  assert.doesNotMatch(home, /<strong>Proxy<\/strong>/)
  assert.match(home, /store\.overview/)
  assert.match(home, /topWeakTag/)
})

test('举手提问流式接口使用 POST JSON，避免问题内容出现在 URL', async () => {
  const api = await source('api/study.ts')
  const http = await source('api/http.ts')
  assert.match(api, /ask\/stream/)
  assert.match(api, /apiPostSse/)
  assert.match(http, /method:\s*'POST'/)
  assert.match(http, /text\/event-stream/)
  assert.match(http, /cache:\s*'no-store'/)
})

test('低分提交结果支持异步深度讲评任务', async () => {
  const types = await source('api/types.ts')
  const store = await source('stores/study.ts')
  assert.match(types, /aiReviewTaskId/)
  assert.match(store, /pollAiReview/)
  assert.match(store, /deepReview/)
})
test('保存 Runtime Key 时同步保存当前 Provider 和默认模型且不回显 Key', async () => {
  const runtime = await source('views/RuntimeStatusView.vue')
  assert.match(runtime, /saveUserProviderSecret\(providerCode,\s*\{ apiKey: apiKey\.value \}\)/)
  assert.match(runtime, /const selectedModel = model\.value\.trim\(\) \|\| selectedProvider\.value\?\.defaultModel/)
  assert.match(runtime, /saveUserAiPreference\(\{ providerCode, model: selectedModel \}\)/)
  assert.match(runtime, /apiKey\.value = ''/)
  assert.match(runtime, /后续学习将使用/)
})

test('学习 AI 响应类型公开 Provider 模型和降级元数据', async () => {
  const types = await source('api/types.ts')
  assert.match(types, /interface AiRuntimeMetadata[\s\S]*providerCode:\s*string\s*\|\s*null[\s\S]*model:\s*string\s*\|\s*null[\s\S]*degraded:\s*boolean/)
  assert.match(types, /interface AskResponse extends AiRuntimeMetadata/)
  assert.match(types, /interface AiReviewStatusResponse extends AiRuntimeMetadata/)
  assert.match(types, /interface OverviewAdviceResponse extends AiRuntimeMetadata[\s\S]*teacherSummary:\s*string[\s\S]*suggestions:\s*string\[\][\s\S]*averageScore:\s*number[\s\S]*weakTags:\s*string\[\][\s\S]*courseTitles:\s*string\[\][\s\S]*hasLearningData:\s*boolean/)
  assert.match(types, /interface ReviewResponse extends AiRuntimeMetadata/)
})

test('SSE done 元数据进入逐节点多轮历史且流失败回退普通 POST', async () => {
  const api = await source('api/study.ts')
  const store = await source('stores/study.ts')
  assert.match(api, /onDone:\s*\(metadata:\s*AiRuntimeMetadata\)/)
  assert.match(store, /askHistoryByNode/)
  assert.match(store, /role:\s*'user'/)
  assert.match(store, /role:\s*'assistant'/)
  assert.match(store, /const assistant = reactive<AskMessage>\(\{/)
  assert.match(store, /onDelta:[\s\S]*assistant\.content\s*=\s*sanitizeAiAnswer\(assistant\.content \+ text\)/)
  assert.match(store, /let receivedDone = false/)
  assert.match(store, /onDone:[\s\S]*receivedDone = true[\s\S]*providerCode[\s\S]*model[\s\S]*degraded/)
  assert.match(store, /if \(!receivedDone \|\| !assistant\.content\.trim\(\)\) throw/)
  assert.match(store, /await askQuestion/)
  assert.match(store, /askHistoryByNode\.value\s*=\s*\{\}/)
})

test('AskBox 展示多轮对话、连接状态、增量光标和运行来源', async () => {
  const askBox = await source('components/AskBox.vue')
  assert.match(askBox, /messages/)
  assert.match(askBox, /v-for=.*message/)
  assert.match(askBox, /正在连接/)
  assert.match(askBox, /stream-cursor/)
  assert.match(askBox, /providerCode/)
  assert.match(askBox, /model/)
  assert.match(askBox, /演示|降级/)
})

test('所有课堂节点默认展开问小绒老师 AI 且切节点读取各自历史', async () => {
  const classroom = await source('views/ClassroomView.vue')
  assert.match(classroom, /title="问小绒老师 AI"/)
  assert.match(classroom, /:start-open="true"/)
  assert.match(classroom, /store\.currentAskHistory/)
  assert.match(classroom, /:connection-status="store\.askConnectionStatus"/)
  assert.equal((classroom.match(/<AskBox/g) || []).length, 1)
})

test('深度讲评轮询 60 秒并覆盖失败超时与手动重试', async () => {
  const store = await source('stores/study.ts')
  const result = await source('components/NodeResult.vue')
  assert.match(store, /attempt < 60/)
  assert.match(store, /delay\(1000\)/)
  assert.match(store, /status:\s*'timeout'/)
  assert.match(store, /retryAiReview/)
  assert.match(result, /pending/)
  assert.match(result, /completed/)
  assert.match(result, /failed/)
  assert.match(result, /timeout/)
  assert.match(result, /重新获取/)
})

test('首页加载并刷新学习建议，复盘页展示后端建议运行状态', async () => {
  const api = await source('api/study.ts')
  const store = await source('stores/study.ts')
  const home = await source('views/HomeView.vue')
  const review = await source('views/ReviewView.vue')
  assert.match(api, /getOverviewAdvice/)
  assert.match(api, /\/api\/study\/overview\/advice/)
  assert.match(store, /overviewAdvice/)
  assert.match(store, /loadOverviewAdvice/)
  assert.match(home, /小绒老师的学习建议/)
  assert.match(home, /store\.overviewAdvice\?\.suggestions/)
  assert.match(home, /刷新建议/)
  assert.match(home, /hasLearningData/)
  assert.match(review, /store\.review\.providerCode/)
  assert.match(review, /store\.review\.model/)
  assert.match(review, /store\.review\.degraded/)
})


test('real SSE preserves runtime metadata and disables Nginx buffering', async () => {
  const api = await source('api/study.ts')
  const nginx = await readFile(new URL('../../nginx.conf', import.meta.url), 'utf8')
  assert.match(api, /providerCode:\s*typeof data\.providerCode === 'string' \? data\.providerCode : null/)
  assert.match(api, /model:\s*typeof data\.model === 'string' \? data\.model : null/)
  assert.match(nginx, /proxy_buffering\s+off;/)
  assert.match(nginx, /proxy_read_timeout\s+3600s;/)
})


