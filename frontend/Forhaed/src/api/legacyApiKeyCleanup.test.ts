import test from 'node:test'
import assert from 'node:assert/strict'
import { clearLegacyApiKeys } from './legacyApiKeyCleanup.ts'

test('removes every legacy api key without reading or uploading values', () => {
  const removed: string[] = []
  let reads = 0
  clearLegacyApiKeys({
    removeItem: (key) => removed.push(key),
    getItem: () => {
      reads += 1
      return 'must-not-be-read'
    },
  })

  assert.deepEqual(removed, [
    'runtimeApiKey',
    'apiKey',
    'apikey',
    'OPENAI_API_KEY',
    'aiApiKey',
  ])
  assert.equal(reads, 0)
})