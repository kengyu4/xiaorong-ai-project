import { apiDelete, apiGet, apiPost, apiPut } from './http'
import type {
  DeleteSecretResult,
  ProviderModelsResult,
  ProviderTestResult,
  RuntimeStatus,
  SavePreferenceRequest,
  SaveSecretRequest,
  UserAiPreference,
  UserAiSettings,
  UserProviderSetting,
} from './types'

export function getRuntimeStatus() {
  return apiGet<RuntimeStatus>('/api/admin/runtime/status')
}

export function getUserAiSettings() {
  return apiGet<UserAiSettings>('/api/user/ai/settings')
}

export function saveUserProviderSecret(providerCode: string, request: SaveSecretRequest) {
  return apiPut<UserProviderSetting, SaveSecretRequest>(
    `/api/user/ai/providers/${encodeURIComponent(providerCode)}/secret`,
    request,
  )
}

export function deleteUserProviderSecret(providerCode: string) {
  return apiDelete<DeleteSecretResult>(
    `/api/user/ai/providers/${encodeURIComponent(providerCode)}/secret`,
  )
}

export function testUserProvider(providerCode: string) {
  return apiPost<ProviderTestResult>(
    `/api/user/ai/providers/${encodeURIComponent(providerCode)}/test`,
  )
}

export function getUserProviderModels(providerCode: string) {
  return apiGet<ProviderModelsResult>(
    `/api/user/ai/providers/${encodeURIComponent(providerCode)}/models`,
  )
}

export function saveUserAiPreference(request: SavePreferenceRequest) {
  return apiPut<UserAiPreference, SavePreferenceRequest>('/api/user/ai/preference', request)
}