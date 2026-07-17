const LEGACY_API_KEY_STORAGE_KEYS = [
  'runtimeApiKey',
  'apiKey',
  'apikey',
  'OPENAI_API_KEY',
  'aiApiKey',
] as const

export interface LegacyStorageCleanupTarget {
  removeItem(key: string): void
  getItem?(key: string): string | null
}

export function clearLegacyApiKeys(storage: LegacyStorageCleanupTarget) {
  for (const key of LEGACY_API_KEY_STORAGE_KEYS) {
    try {
      storage.removeItem(key)
    } catch {
      // Storage may be blocked by the browser; never read or migrate legacy values.
    }
  }
}