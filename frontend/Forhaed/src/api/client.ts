import type { ApiResponse } from './types'

const BASE_URL = 'http://localhost:8088'

export class ApiError extends Error {
  constructor(
    message: string,
    public code: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  if (!res.ok) {
    throw new ApiError(`请求失败：${res.status}`, res.status)
  }

  const json: ApiResponse<T> = await res.json()

  if (json.code !== 200) {
    throw new ApiError(json.message || '接口返回异常', json.code)
  }

  return json.data
}

export const api = {
  get<T>(path: string): Promise<T> {
    return request<T>(path)
  },

  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    })
  },
}
