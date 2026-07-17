export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export type ApiResponse<T> = ApiResult<T>

export interface AuthUser {
  userId: number
  username: string
  nickname?: string
  roles: string[]
}

export interface AuthSession {
  token: string
  user: AuthUser
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  nickname?: string
}

export interface CourseSummary {
  courseId: number
  title: string
  description: string
  difficulty: string
  tags: string[]
  lessonCount: number
  homeworkCount: number
  teacherAvatar: string
  classmateAvatar: string
}

export type Course = CourseSummary

export interface PersonaView {
  name: string
  avatar: string
  role: string
}

export interface ClassmateView {
  name: string
  avatar: string
  bondValue: number
}

export interface Reward {
  rightBond: number
  wrongBond: number
}

export type LessonNodeType = 'lecture' | 'checkpoint' | 'classmate' | 'homework_intro'

export interface LessonNode {
  nodeId: string
  type: LessonNodeType | string
  speaker: string
  title: string
  text: string | null
  knowledgePoint: string
  question: string | null
  answerType: string | null
  answerKeywords: string[]
  standardAnswer: string | null
  explanation: string | null
  reward: Reward | null
}

export interface LessonScriptResponse {
  courseId: number
  title: string
  teacher: PersonaView
  classmate: ClassmateView
  nodes: LessonNode[]
}

export interface SessionCreateResponse {
  sessionId: number
  courseId: number
  status: string
  currentNodeIndex: number
  mode?: string
}

export interface SubmitAnswerRequest {
  answerText: string
}

export interface NodeSubmitResponse {
  score: number
  hitKeywords: string[]
  missKeywords: string[]
  feedback: string
  teacherReply: string
  nextNodeIndex: number
  needAiReview: boolean
  aiReviewTaskId: string | null
}

export interface ClassmateSubmitResponse {
  score: number
  bondDelta: number
  bondValue: number
  hitKeywords: string[]
  missKeywords: string[]
  classmateReply: string
  teacherSupplement: string
}

export type NodeResult = NodeSubmitResponse | ClassmateSubmitResponse

export interface AiRuntimeMetadata {
  providerCode: string | null
  model: string | null
  degraded: boolean
}

export interface AskResponse extends AiRuntimeMetadata {
  answer: string
  relatedKeywords: string[]
}

export interface AskMessage extends AiRuntimeMetadata {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  fallback?: boolean
}

export interface HomeworkItem {
  topicId: number
  title: string
  body: string
  tags: string[]
  difficulty: string
}

export interface HomeworkResponse {
  items: HomeworkItem[]
}

export interface HomeworkSubmitResponse {
  score: number
  hitKeywords: string[]
  missKeywords: string[]
  feedback: string
  aiReview: string
  standardAnswer: string
  needAiReview: boolean
  aiReviewTaskId: string | null
}

export interface AiReviewStatusResponse extends AiRuntimeMetadata {
  taskId: string
  status: 'pending' | 'completed' | 'failed' | 'timeout'
  content: string | null
}

export interface StudyOverviewResponse {
  topWeakTag: string | null
  weakTags: string[]
  weakTagCount: number
  completedCount: number
}
export interface OverviewAdviceResponse extends AiRuntimeMetadata {
  teacherSummary: string
  suggestions: string[]
  averageScore: number
  weakTags: string[]
  courseTitles: string[]
  hasLearningData: boolean
}

export interface InterviewFollowUpResponse {
  mode: 'fixed' | 'ai' | 'none'
  question: string | null
  followUpLevel: number
}

export interface TokenBudgetStatusResponse {
  date: string
  requests: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  degradedRequests: number
  warning: boolean
  exhausted: boolean
  warningLimit: number
  hardLimit: number
}

export interface ReviewResponse extends AiRuntimeMetadata {
  averageScore: number
  bondValue: number
  weakTags: string[]
  summary: string
  teacherSummary: string
  classmateReply: string
  nextActions: string[]
  nextCourse: {
    courseId: number
    title: string
  }
}

export interface RuntimeStatus {
  persistenceEnabled: boolean
  studyService: string
  cacheEnabled: boolean
  redisReachable: boolean
  aiRealEnabled: boolean
  defaultProviderCode: string
  aiGatewayService: string
  providers: ProviderStatus[]
}

export interface ProviderStatus {
  providerCode: string
  protocol: string
  baseUrl: string
  defaultModel: string
  enabled: boolean
  apiKeyConfigured: boolean
}

export interface UserAiSettings {
  persistenceAvailable: boolean
  secureStorageAvailable: boolean
  providerCode: string | null
  model: string | null
  providers: UserProviderSetting[]
}

export interface UserProviderSetting {
  providerCode: string
  providerName: string
  protocol: string
  defaultModel: string
  enabled: boolean
  configured: boolean
  maskedApiKey: string | null
}

export interface SaveSecretRequest {
  apiKey: string
}

export interface SavePreferenceRequest {
  providerCode: string
  model: string
}

export interface ProviderTestResult {
  success: boolean
  latencyMs: number
  providerCode: string
  model: string | null
  message: string
}

export interface ProviderModelsResult {
  providerCode: string
  models: string[]
}

export interface DeleteSecretResult {
  deleted: boolean
}

export interface UserAiPreference {
  providerCode: string
  model: string
}
