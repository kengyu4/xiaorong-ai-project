import { clearStoredSession, readStoredToken } from './authSession'

const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

export class ApiError extends Error {
  constructor(message: string, public readonly status?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler) { unauthorizedHandler = handler }
export async function apiGet<T>(path: string): Promise<T> { return request<T>(path, undefined, true) }
export async function apiPut<T, B = unknown>(path: string, body?: B): Promise<T> { return request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }, true) }
export async function apiDelete<T>(path: string): Promise<T> { return request<T>(path, { method: 'DELETE' }, true) }
export async function apiPost<T, B = unknown>(path: string, body?: B): Promise<T> { return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }, true) }
export async function apiGetRaw<T>(path: string): Promise<T> { return request<T>(path) }
export async function apiPostRaw<T, B = unknown>(path: string, body?: B): Promise<T> { return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }) }

export interface SseHandlers {
  onEvent?: (event: string, data: unknown) => void
  onDelta?: (text: string) => void
}

export async function apiPostSse<B = unknown>(path: string, body: B, handlers: SseHandlers = {}) {
  const headers = new Headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' })
  const token = readStoredToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${baseUrl}${path}`, { method: 'POST', headers, body: JSON.stringify(body), cache: 'no-store' })
  if (response.status === 401) {
    handleUnauthorized()
    throw new ApiError('登录已过期，请重新登录', response.status)
  }
  if (!response.ok) {
    const payload = await readResponsePayload(response)
    throw new ApiError(readErrorMessage(payload) || `请求失败：${response.status}`, response.status)
  }
  if (!response.body) throw new ApiError('浏览器不支持流式响应', response.status)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) dispatchSseBlock(block, handlers)
    if (done) break
  }
  if (buffer.trim()) dispatchSseBlock(buffer, handlers)
}

function dispatchSseBlock(block: string, handlers: SseHandlers) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return
  const raw = dataLines.join('\n')
  let data: unknown = raw
  try { data = JSON.parse(raw) } catch { /* plain text SSE */ }
  handlers.onEvent?.(event, data)
  if (event === 'delta' && isRecord(data) && typeof data.text === 'string') handlers.onDelta?.(data.text)
}

async function request<T>(path: string, init?: RequestInit, unwrapApiResult = false): Promise<T> {
  const headers = new Headers(init?.headers)
  const token = readStoredToken()
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)
  if (init?.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${baseUrl}${path}`, { ...init, headers })
  const payload = await readResponsePayload(response)
  if (response.status === 401) { handleUnauthorized(); throw new ApiError(readErrorMessage(payload) || '登录已过期，请重新登录', response.status) }
  if (!response.ok) throw new ApiError(readErrorMessage(payload) || `请求失败：${response.status}`, response.status)
  if (payload === undefined) return undefined as T
  if (!unwrapApiResult) return payload as T
  if (!isRecord(payload) || typeof payload.code !== 'number') throw new ApiError('服务返回格式异常', response.status)
  if (payload.code !== 200) throw new ApiError(readErrorMessage(payload) || '服务返回异常', response.status)
  return payload.data as T
}

async function readResponsePayload(response: Response) {
  const text = await response.text()
  if (!text) return undefined
  try { return JSON.parse(text) as unknown } catch { return text }
}
function readErrorMessage(payload: unknown) {
  if (isRecord(payload) && typeof payload.message === 'string') return payload.message
  if (typeof payload === 'string' && payload.length <= 120) return payload
  return ''
}
function handleUnauthorized() {
  clearStoredSession()
  if (unauthorizedHandler) { unauthorizedHandler(); return }
  if (typeof window === 'undefined' || window.location.pathname === '/login') return
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
}
function isRecord(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }