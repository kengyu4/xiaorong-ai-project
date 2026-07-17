import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, loginAuth, logoutAuth, registerAuth } from '@/api/auth'
import { ApiError } from '@/api/http'
import {
  clearStoredSession,
  readStoredToken,
  readStoredUser,
  writeStoredSession,
  writeStoredUser,
} from '@/api/authSession'
import type { AuthSession, AuthUser, LoginRequest, RegisterRequest } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readStoredToken())
  const user = ref<AuthUser | null>(readStoredUser())
  const meChecked = ref(false)
  let mePromise: Promise<boolean> | null = null

  const username = computed(() => user.value?.nickname || user.value?.username || '')
  const isLoggedIn = computed(() => token.value.length > 0)

  function setSession(session: AuthSession) {
    token.value = session.token
    user.value = session.user
    meChecked.value = true
    writeStoredSession(session)
  }

  function clearSession() {
    token.value = ''
    user.value = null
    meChecked.value = false
    clearStoredSession()
  }

  async function login(payload: LoginRequest) {
    const session = await loginAuth({
      username: payload.username.trim(),
      password: payload.password,
    })
    setSession(session)
    return session.user
  }

  async function register(payload: RegisterRequest) {
    const nickname = payload.nickname?.trim()
    const session = await registerAuth({
      username: payload.username.trim(),
      password: payload.password,
      ...(nickname ? { nickname } : {}),
    })
    setSession(session)
    return session.user
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutAuth()
      }
    } finally {
      clearSession()
    }
  }

  async function ensureSession() {
    if (!token.value) {
      clearSession()
      return false
    }

    if (meChecked.value) return true
    if (mePromise) return mePromise

    mePromise = getCurrentUser()
      .then((currentUser) => {
        user.value = currentUser
        meChecked.value = true
        writeStoredUser(currentUser)
        return true
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          clearSession()
          return false
        }

        meChecked.value = true
        return true
      })
      .finally(() => {
        mePromise = null
      })

    return mePromise
  }

  return {
    token,
    user,
    username,
    isLoggedIn,
    setSession,
    clearSession,
    login,
    register,
    logout,
    ensureSession,
  }
})
