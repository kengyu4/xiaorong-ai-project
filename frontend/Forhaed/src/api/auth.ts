import { apiGetRaw, apiPostRaw, ApiError } from './http'
import type { AuthSession, AuthUser, LoginRequest, RegisterRequest } from './types'

export function loginAuth(payload: LoginRequest) {
  return apiPostRaw<unknown, LoginRequest>('/api/auth/login', payload).then(normalizeAuthSession)
}

export function registerAuth(payload: RegisterRequest) {
  return apiPostRaw<unknown, RegisterRequest>('/api/auth/register', payload).then(normalizeAuthSession)
}

export function getCurrentUser() {
  return apiGetRaw<unknown>('/api/auth/me').then(normalizeMeResponse)
}

export function logoutAuth() {
  return apiPostRaw<void>('/api/auth/logout')
}

function normalizeAuthSession(payload: unknown): AuthSession {
  const data = unwrapMaybe(payload)

  if (!isRecord(data) || typeof data.token !== 'string' || !data.token) {
    throw new ApiError('登录返回缺少 token')
  }

  if (!('user' in data)) {
    throw new ApiError('登录返回缺少用户信息')
  }

  return {
    token: data.token,
    user: normalizeUser(data.user),
  }
}

function normalizeMeResponse(payload: unknown): AuthUser {
  const data = unwrapMaybe(payload)

  if (isRecord(data) && 'user' in data) {
    return normalizeUser(data.user)
  }

  return normalizeUser(data)
}

function normalizeUser(value: unknown): AuthUser {
  if (!isRecord(value) || typeof value.username !== 'string') {
    throw new ApiError('用户信息格式异常')
  }

  const user: AuthUser = {
    userId: Number(value.userId ?? 0),
    username: value.username,
    roles: Array.isArray(value.roles)
      ? value.roles.filter((role): role is string => typeof role === 'string')
      : [],
  }

  if (typeof value.nickname === 'string') {
    user.nickname = value.nickname
  }

  return user
}

function unwrapMaybe(payload: unknown) {
  if (isRecord(payload) && typeof payload.code === 'number' && 'data' in payload) {
    if (payload.code !== 200) {
      throw new ApiError(readMessage(payload) || '服务返回异常')
    }

    return payload.data
  }

  return payload
}

function readMessage(value: Record<string, unknown>) {
  return typeof value.message === 'string' ? value.message : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
