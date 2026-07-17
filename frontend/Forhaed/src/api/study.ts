import { apiGet, apiPost, apiPostSse } from './http'
import type {
  AiReviewStatusResponse, AiRuntimeMetadata, AskResponse, ClassmateSubmitResponse, CourseSummary,
  HomeworkResponse, HomeworkSubmitResponse, InterviewFollowUpResponse, LessonScriptResponse,
  NodeSubmitResponse, OverviewAdviceResponse, ReviewResponse, SessionCreateResponse,
  StudyOverviewResponse, SubmitAnswerRequest, TokenBudgetStatusResponse,
} from './types'

export function listCourses(subjectId = 1) { return apiGet<CourseSummary[]>(`/api/study/courses?subjectId=${subjectId}`) }
export function getStudyOverview() { return apiGet<StudyOverviewResponse>('/api/study/overview') }
export function getOverviewAdvice() { return apiGet<OverviewAdviceResponse>('/api/study/overview/advice') }
export function getAiBudget() { return apiGet<TokenBudgetStatusResponse>('/api/study/ai/budget') }
export function createSession(courseId: number, mode = 'immersive') { return apiPost<SessionCreateResponse>('/api/study/sessions', { courseId, mode }) }
export function getScript(sessionId: number) { return apiGet<LessonScriptResponse>(`/api/study/sessions/${sessionId}/script`) }
export function submitNode(sessionId: number, nodeId: string, answerText: string) { return apiPost<NodeSubmitResponse, SubmitAnswerRequest>(`/api/study/sessions/${sessionId}/nodes/${nodeId}/submit`, { answerText }) }
export function submitClassmate(sessionId: number, nodeId: string, answerText: string) { return apiPost<ClassmateSubmitResponse, SubmitAnswerRequest>(`/api/study/sessions/${sessionId}/classmate/${nodeId}/submit`, { answerText }) }
export function askQuestion(sessionId: number, nodeId: string, question: string) { return apiPost<AskResponse>(`/api/study/sessions/${sessionId}/ask`, { nodeId, question }) }
const TYPEWRITER_INTERVAL_MS = 16

export async function streamAskQuestion(
  sessionId: number,
  nodeId: string,
  question: string,
  handlers: { onReady: () => void; onDelta: (text: string) => void; onDone: (metadata: AiRuntimeMetadata) => void },
) {
  let cancelled = false
  let renderQueue: Promise<void> = Promise.resolve()

  try {
    await apiPostSse(`/api/study/sessions/${sessionId}/ask/stream`, { nodeId, question }, {
      onDelta(text) {
        renderQueue = renderQueue.then(() => emitCharacters(text, handlers.onDelta, () => cancelled))
      },
      onEvent(event, data) {
        if (event === 'ready') {
          handlers.onReady()
          return
        }
        if (event !== 'done' || !isRecord(data)) return
        const metadata: AiRuntimeMetadata = {
          providerCode: typeof data.providerCode === 'string' ? data.providerCode : null,
          model: typeof data.model === 'string' ? data.model : null,
          degraded: data.degraded === true,
        }
        renderQueue = renderQueue.then(() => {
          if (!cancelled) handlers.onDone(metadata)
        })
      },
    })
    await renderQueue
  } catch (error) {
    cancelled = true
    throw error
  }
}

async function emitCharacters(text: string, emit: (text: string) => void, isCancelled: () => boolean) {
  for (const character of Array.from(text)) {
    if (isCancelled()) return
    emit(character)
    await delay(TYPEWRITER_INTERVAL_MS)
  }
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, milliseconds))
}

export function getAiReview(sessionId: number, taskId: string) { return apiGet<AiReviewStatusResponse>(`/api/study/sessions/${sessionId}/ai-reviews/${taskId}`) }
export function requestInterviewFollowUp(sessionId: number, nodeId: string, answerText: string, followUpLevel = 0) { return apiPost<InterviewFollowUpResponse>(`/api/study/sessions/${sessionId}/interview/follow-up`, { nodeId, answerText, followUpLevel }) }
export function getHomework(sessionId: number) { return apiGet<HomeworkResponse>(`/api/study/sessions/${sessionId}/homework`) }
export function submitHomework(sessionId: number, topicId: number, answerText: string) { return apiPost<HomeworkSubmitResponse, SubmitAnswerRequest>(`/api/study/sessions/${sessionId}/homework/${topicId}/submit`, { answerText }) }
export function getReview(sessionId: number) { return apiGet<ReviewResponse>(`/api/study/sessions/${sessionId}/review`) }

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
