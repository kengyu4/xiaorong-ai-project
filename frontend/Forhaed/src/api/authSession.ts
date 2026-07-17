import type { AuthSession, AuthUser } from './types'

export const authTokenStorageKey = 'xiaorong-auth-token'
export const authUserStorageKey = 'xiaorong-auth-user'

function getStorage() {
  if (typeof window === 'undefined') return null

  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function readStoredToken() {
  return getStorage()?.getItem(authTokenStorageKey) ?? ''
}

export function readStoredUser(): AuthUser | null {
  const raw = getStorage()?.getItem(authUserStorageKey)
  if (!raw) return null

  try {
    return normalizeStoredUser(JSON.parse(raw))
  } catch {
    return null
  }
}

export function writeStoredSession(session: AuthSession) {
  const storage = getStorage()
  if (!storage) return

  storage.setItem(authTokenStorageKey, session.token)
  storage.setItem(authUserStorageKey, JSON.stringify(session.user))
}

export function writeStoredUser(user: AuthUser) {
  getStorage()?.setItem(authUserStorageKey, JSON.stringify(user))
}

export function clearStoredSession() {
  const storage = getStorage()
  if (!storage) return

  storage.removeItem(authTokenStorageKey)
  storage.removeItem(authUserStorageKey)
}

function normalizeStoredUser(value: unknown): AuthUser | null {
  if (!isRecord(value) || typeof value.username !== 'string') return null

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
