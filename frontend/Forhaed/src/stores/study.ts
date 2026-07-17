import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  askQuestion,
  createSession,
  getAiBudget,
  getAiReview,
  getHomework,
  getOverviewAdvice,
  getReview,
  getScript,
  getStudyOverview,
  listCourses,
  streamAskQuestion,
  submitClassmate as submitClassmateAnswer,
  submitHomework as submitHomeworkAnswer,
  submitNode as submitLessonNode,
} from '@/api/study'
import type {
  AiReviewStatusResponse,
  AskMessage,
  AskResponse,
  ClassmateSubmitResponse,
  CourseSummary,
  HomeworkItem,
  HomeworkSubmitResponse,
  LessonNode,
  LessonScriptResponse,
  NodeSubmitResponse,
  OverviewAdviceResponse,
  ReviewResponse,
  SessionCreateResponse,
  StudyOverviewResponse,
  TokenBudgetStatusResponse,
} from '@/api/types'

type AskConnectionStatus = 'idle' | 'connecting' | 'streaming' | 'completed' | 'fallback' | 'failed'
type ReviewTarget = 'node' | 'homework'

export const useStudyStore = defineStore('study', () => {
  const courses = ref<CourseSummary[]>([])
  const overview = ref<StudyOverviewResponse | null>(null)
  const overviewAdvice = ref<OverviewAdviceResponse | null>(null)
  const overviewAdviceLoading = ref(false)
  const overviewAdviceError = ref('')
  const aiBudget = ref<TokenBudgetStatusResponse | null>(null)
  const session = ref<SessionCreateResponse | null>(null)
  const script = ref<LessonScriptResponse | null>(null)
  const nodeIndex = ref(0)
  const homework = ref<HomeworkItem[]>([])
  const homeworkIndex = ref(0)
  const review = ref<ReviewResponse | null>(null)
  const nodeResult = ref<NodeSubmitResponse | ClassmateSubmitResponse | null>(null)
  const homeworkResult = ref<HomeworkSubmitResponse | null>(null)
  const askHistoryByNode = ref<Record<string, AskMessage[]>>({})
  const askStatusByNode = ref<Record<string, AskConnectionStatus>>({})
  const askStreaming = ref(false)
  const nodeDeepReview = ref<AiReviewStatusResponse | null>(null)
  const homeworkDeepReview = ref<AiReviewStatusResponse | null>(null)
  const nodeDeepReviewTaskId = ref<string | null>(null)
  const homeworkDeepReviewTaskId = ref<string | null>(null)
  const deepReviewLoading = ref(false)
  const records = ref<string[]>([])
  const loading = ref(false)
  const startingCourseId = ref<number | null>(null)
  const error = ref('')

  const activeNode = computed<LessonNode | null>(() => script.value?.nodes[nodeIndex.value] || null)
  const currentNode = activeNode
  const activeHomework = computed<HomeworkItem | null>(() => homework.value[homeworkIndex.value] || null)
  const currentAskHistory = computed(() => activeNode.value ? askHistoryByNode.value[activeNode.value.nodeId] || [] : [])
  const askConnectionStatus = computed<AskConnectionStatus>(() => activeNode.value ? askStatusByNode.value[activeNode.value.nodeId] || 'idle' : 'idle')
  const askResult = computed<AskResponse | null>(() => {
    const message = [...currentAskHistory.value].reverse().find((item) => item.role === 'assistant')
    if (!message) return null
    return {
      answer: message.content,
      relatedKeywords: activeNode.value?.answerKeywords || [],
      providerCode: message.providerCode,
      model: message.model,
      degraded: message.degraded,
    }
  })
  const isInSession = computed(() => Boolean(session.value))
  const isInHomework = computed(() => homework.value.length > 0)
  const progressText = computed(() => {
    const total = script.value?.nodes.length || 0
    return total ? `${Math.min(nodeIndex.value + 1, total)} / ${total}` : '0 / 0'
  })

  async function loadCourses() {
    return withLoading(async () => {
      courses.value = await listCourses()
      pushRecord('已同步课程列表')
      return courses.value
    })
  }

  async function loadOverview() {
    overview.value = await getStudyOverview()
    return overview.value
  }

  async function loadOverviewAdvice() {
    overviewAdviceLoading.value = true
    overviewAdviceError.value = ''
    try {
      overviewAdvice.value = await getOverviewAdvice()
      return overviewAdvice.value
    } catch (err) {
      overviewAdviceError.value = err instanceof Error ? err.message : '学习建议加载失败'
      throw err
    } finally {
      overviewAdviceLoading.value = false
    }
  }

  async function loadAiBudget() {
    aiBudget.value = await getAiBudget()
    return aiBudget.value
  }

  async function startCourse(courseId: number, selectedMode = 'immersive') {
    startingCourseId.value = courseId
    try {
      return await withLoading(async () => {
        const created = await createSession(courseId, selectedMode)
        session.value = { ...created, mode: selectedMode }
        script.value = await getScript(created.sessionId)
        nodeIndex.value = created.currentNodeIndex || 0
        homework.value = []
        homeworkIndex.value = 0
        review.value = null
        nodeResult.value = null
        homeworkResult.value = null
        clearConversationState()
        clearDeepReviewState()
        pushRecord(`创建学习会话 #${created.sessionId}`)
        return session.value
      })
    } finally {
      startingCourseId.value = null
    }
  }

  const startSession = startCourse

  async function loadScript(sessionId: number) {
    return withLoading(async () => {
      const isNewSession = !session.value || session.value.sessionId !== sessionId
      script.value = await getScript(sessionId)
      if (isNewSession) {
        session.value = { sessionId, courseId: script.value.courseId, status: 'learning', currentNodeIndex: 0, mode: 'immersive' }
        nodeIndex.value = 0
        clearConversationState()
        clearDeepReviewState()
      }
      nodeIndex.value = Math.min(nodeIndex.value, Math.max(0, script.value.nodes.length - 1))
      pushRecord('已载入课堂脚本')
      return script.value
    })
  }

  function clearNodeFeedback() {
    nodeResult.value = null
    nodeDeepReview.value = null
    nodeDeepReviewTaskId.value = null
  }
  function goPrevNode() { clearNodeFeedback(); nodeIndex.value = Math.max(0, nodeIndex.value - 1) }
  function goNextNode() { clearNodeFeedback(); nodeIndex.value = Math.min(Math.max(0, (script.value?.nodes.length || 1) - 1), nodeIndex.value + 1) }
  const goToPrevNode = goPrevNode
  const goToNextNode = goNextNode

  async function answerNode(answerText: string) {
    const node = requireNode()
    return node.type === 'classmate' ? submitClassmate(node.nodeId, answerText) : submitNode(node.nodeId, answerText)
  }

  async function submitNode(nodeId: string, answerText: string) {
    const currentSession = requireSession()
    return withLoading(async () => {
      nodeDeepReview.value = null
      nodeDeepReviewTaskId.value = null
      nodeResult.value = await submitLessonNode(currentSession.sessionId, nodeId, answerText)
      pushRecord(`课堂提问得分 ${nodeResult.value.score}`)
      if (nodeResult.value.aiReviewTaskId) {
        nodeDeepReviewTaskId.value = nodeResult.value.aiReviewTaskId
        void pollAiReview(currentSession.sessionId, nodeResult.value.aiReviewTaskId, 'node')
      }
      void loadOverview().catch(() => undefined)
      void loadOverviewAdvice().catch(() => undefined)
      return nodeResult.value
    })
  }

  async function submitClassmate(nodeId: string, answerText: string) {
    const currentSession = requireSession()
    return withLoading(async () => {
      const result = await submitClassmateAnswer(currentSession.sessionId, nodeId, answerText)
      nodeResult.value = result
      if (script.value) script.value.classmate.bondValue = result.bondValue
      pushRecord(`白子互助得分 ${result.score}，协作值 ${result.bondValue}`)
      void loadOverview().catch(() => undefined)
      void loadOverviewAdvice().catch(() => undefined)
      return result
    })
  }

  async function ask(nodeIdOrQuestion: string, maybeQuestion?: string) {
    const currentSession = requireSession()
    const nodeId = maybeQuestion ? nodeIdOrQuestion : requireNode().nodeId
    const question = (maybeQuestion || nodeIdOrQuestion).trim()
    if (!question) return null

    const history = ensureAskHistory(nodeId)
    history.push({
      id: createMessageId('user'),
      role: 'user',
      content: question,
      providerCode: null,
      model: null,
      degraded: false,
    })
    const assistant = reactive<AskMessage>({
      id: createMessageId('assistant'),
      role: 'assistant',
      content: '',
      providerCode: null,
      model: null,
      degraded: false,
      streaming: true,
    })
    history.push(assistant)
    askStreaming.value = true
    setAskStatus(nodeId, 'connecting')
    error.value = ''

    try {
      let receivedDone = false
      await streamAskQuestion(currentSession.sessionId, nodeId, question, {
        onReady: () => setAskStatus(nodeId, 'streaming'),
        onDelta: (text) => {
          setAskStatus(nodeId, 'streaming')
          assistant.content = sanitizeAiAnswer(assistant.content + text)
        },
        onDone: (metadata) => {
          receivedDone = true
          assistant.providerCode = metadata.providerCode
          assistant.model = metadata.model
          assistant.degraded = metadata.degraded
        },
      })
      if (!receivedDone || !assistant.content.trim()) throw new Error('流式回答未完整结束')
      assistant.streaming = false
      setAskStatus(nodeId, assistant.degraded ? 'fallback' : 'completed')
      pushRecord('小绒老师已完成流式回答')
      void loadAiBudget().catch(() => undefined)
      return askResult.value
    } catch (streamError) {
      try {
        setAskStatus(nodeId, 'fallback')
        const fallback = await askQuestion(currentSession.sessionId, nodeId, question)
        Object.assign(assistant, {
          content: sanitizeAiAnswer(fallback.answer),
          providerCode: fallback.providerCode,
          model: fallback.model,
          degraded: fallback.degraded,
          streaming: false,
          fallback: true,
        })
        pushRecord('流式连接失败，已通过普通请求完成回答')
        void loadAiBudget().catch(() => undefined)
        return fallback
      } catch (fallbackError) {
        assistant.streaming = false
        assistant.content ||= '这次连接没有成功，请稍后重试。'
        assistant.degraded = true
        assistant.fallback = true
        setAskStatus(nodeId, 'failed')
        error.value = fallbackError instanceof Error
          ? fallbackError.message
          : streamError instanceof Error ? streamError.message : '提问失败'
        throw fallbackError
      }
    } finally {
      askStreaming.value = false
    }
  }

  async function pollAiReview(sessionId: number, taskId: string, target: ReviewTarget) {
    deepReviewLoading.value = true
    try {
      for (let attempt = 0; attempt < 60; attempt += 1) {
        const task = await getAiReview(sessionId, taskId)
        setDeepReview(target, task)
        if (task.status !== 'pending') return task
        await delay(1000)
      }
      const current = getDeepReview(target)
      const timeout: AiReviewStatusResponse = {
        taskId,
        status: 'timeout',
        content: null,
        providerCode: current?.providerCode || 'mock',
        model: current?.model || '演示模型',
        degraded: current?.degraded ?? true,
      }
      setDeepReview(target, timeout)
      return timeout
    } catch (err) {
      const current = getDeepReview(target)
      const failed: AiReviewStatusResponse = {
        taskId,
        status: 'failed',
        content: err instanceof Error ? err.message : '深度讲评获取失败',
        providerCode: current?.providerCode || 'mock',
        model: current?.model || '演示模型',
        degraded: current?.degraded ?? true,
      }
      setDeepReview(target, failed)
      return failed
    } finally {
      deepReviewLoading.value = false
    }
  }

  async function retryAiReview(target: ReviewTarget = 'node') {
    const currentSession = requireSession()
    const taskId = target === 'node' ? nodeDeepReviewTaskId.value : homeworkDeepReviewTaskId.value
    if (!taskId) throw new Error('没有可重试的深度讲评任务')
    setDeepReview(target, {
      taskId,
      status: 'pending',
      content: null,
      providerCode: null,
      model: null,
      degraded: false,
    })
    return pollAiReview(currentSession.sessionId, taskId, target)
  }

  async function loadHomework(sessionId?: number) {
    const targetSessionId = sessionId || requireSession().sessionId
    return withLoading(async () => {
      ensureSession(targetSessionId)
      homework.value = (await getHomework(targetSessionId)).items
      homeworkIndex.value = 0
      homeworkResult.value = null
      homeworkDeepReview.value = null
      homeworkDeepReviewTaskId.value = null
      pushRecord(`已布置 ${homework.value.length} 道作业`)
      return homework.value
    })
  }

  function goPrevHomework() { homeworkResult.value = null; homeworkDeepReview.value = null; homeworkDeepReviewTaskId.value = null; homeworkIndex.value = Math.max(0, homeworkIndex.value - 1) }
  function goNextHomework() { homeworkResult.value = null; homeworkDeepReview.value = null; homeworkDeepReviewTaskId.value = null; homeworkIndex.value = Math.min(Math.max(0, homework.value.length - 1), homeworkIndex.value + 1) }
  const goToPrevHomework = goPrevHomework
  const goToNextHomework = goNextHomework

  async function answerHomework(answerText: string) {
    const item = activeHomework.value
    if (!item) throw new Error('当前没有作业题')
    return submitHomeworkItem(item.topicId, answerText)
  }

  async function submitHomeworkItem(topicId: number, answerText: string) {
    const currentSession = requireSession()
    return withLoading(async () => {
      homeworkDeepReview.value = null
      homeworkDeepReviewTaskId.value = null
      homeworkResult.value = await submitHomeworkAnswer(currentSession.sessionId, topicId, answerText)
      pushRecord(`作业讲评得分 ${homeworkResult.value.score}`)
      if (homeworkResult.value.aiReviewTaskId) {
        homeworkDeepReviewTaskId.value = homeworkResult.value.aiReviewTaskId
        void pollAiReview(currentSession.sessionId, homeworkResult.value.aiReviewTaskId, 'homework')
      }
      void loadOverview().catch(() => undefined)
      void loadOverviewAdvice().catch(() => undefined)
      return homeworkResult.value
    })
  }

  async function loadReview(sessionId?: number) {
    const targetSessionId = sessionId || requireSession().sessionId
    return withLoading(async () => {
      ensureSession(targetSessionId)
      review.value = await getReview(targetSessionId)
      pushRecord('已生成个性化学习复盘')
      void loadAiBudget().catch(() => undefined)
      return review.value
    })
  }

  function reset() {
    session.value = null
    script.value = null
    nodeIndex.value = 0
    homework.value = []
    homeworkIndex.value = 0
    review.value = null
    nodeResult.value = null
    homeworkResult.value = null
    clearConversationState()
    clearDeepReviewState()
    records.value = []
    error.value = ''
  }

  function clearConversationState() {
    askHistoryByNode.value = {}
    askStatusByNode.value = {}
    askStreaming.value = false
  }
  function clearDeepReviewState() {
    nodeDeepReview.value = null
    homeworkDeepReview.value = null
    nodeDeepReviewTaskId.value = null
    homeworkDeepReviewTaskId.value = null
    deepReviewLoading.value = false
  }
  function ensureAskHistory(nodeId: string) {
    if (!askHistoryByNode.value[nodeId]) askHistoryByNode.value[nodeId] = []
    return askHistoryByNode.value[nodeId]
  }
  function setAskStatus(nodeId: string, status: AskConnectionStatus) { askStatusByNode.value[nodeId] = status }
  function setDeepReview(target: ReviewTarget, task: AiReviewStatusResponse) {
    if (target === 'node') nodeDeepReview.value = task
    else homeworkDeepReview.value = task
  }
  function getDeepReview(target: ReviewTarget) { return target === 'node' ? nodeDeepReview.value : homeworkDeepReview.value }
  function createMessageId(role: string) { return `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }
  function requireSession() { if (!session.value) throw new Error('学习会话不存在'); return session.value }
  function ensureSession(sessionId: number) { if (!session.value || session.value.sessionId !== sessionId) session.value = { sessionId, courseId: script.value?.courseId || 0, status: 'learning', currentNodeIndex: nodeIndex.value, mode: 'immersive' } }
  function requireNode() { if (!activeNode.value) throw new Error('当前没有课堂节点'); return activeNode.value }
  function pushRecord(message: string) { records.value = [...records.value, message].slice(-20) }
  function sanitizeAiAnswer(text: string) { return text.replace(/\*{2,}/g, '') }
  function delay(ms: number) { return new Promise((resolve) => setTimeout(resolve, ms)) }
  async function withLoading<T>(task: () => Promise<T>) { loading.value = true; error.value = ''; try { return await task() } catch (err) { error.value = err instanceof Error ? err.message : '请求失败'; throw err } finally { loading.value = false } }

  return {
    courses, overview, overviewAdvice, overviewAdviceLoading, overviewAdviceError, aiBudget, session, script,
    nodeIndex, homework, homeworkIndex, review, nodeResult, homeworkResult, askHistoryByNode,
    currentAskHistory, askConnectionStatus, askResult, askStreaming, nodeDeepReview, homeworkDeepReview,
    deepReviewLoading, records, loading, startingCourseId, error, activeNode, currentNode, activeHomework,
    isInSession, isInHomework, progressText, loadCourses, loadOverview, loadOverviewAdvice, loadAiBudget,
    startCourse, startSession, loadScript, goPrevNode, goNextNode, goToPrevNode, goToNextNode, answerNode,
    submitNode, submitClassmate, ask, pollAiReview, retryAiReview, loadHomework, goPrevHomework,
    goNextHomework, goToPrevHomework, goToNextHomework, answerHomework, submitHomeworkItem, loadReview, reset,
  }
})

