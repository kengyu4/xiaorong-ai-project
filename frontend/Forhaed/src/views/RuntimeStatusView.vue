<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deleteUserProviderSecret,
  getRuntimeStatus,
  getUserAiSettings,
  getUserProviderModels,
  saveUserAiPreference,
  saveUserProviderSecret,
  testUserProvider,
} from '@/api/admin'
import type { RuntimeStatus, UserAiSettings } from '@/api/types'

const status = ref<RuntimeStatus | null>(null)
const userSettings = ref<UserAiSettings | null>(null)
const selectedProviderCode = ref('')
const apiKey = ref('')
const model = ref('')
const models = ref<string[]>([])
const loading = ref(false)
const action = ref('')
const error = ref('')
const message = ref('')
const messageTone = ref<'success' | 'warning'>('success')

const providerText = computed(() => {
  if (!status.value) return '未检测'
  return status.value.aiRealEnabled ? status.value.defaultProviderCode : 'mock'
})

const selectedProvider = computed(() =>
  userSettings.value?.providers.find(
    (provider) => provider.providerCode === selectedProviderCode.value,
  ),
)

const busy = computed(() => action.value !== '')

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const [runtime, settings] = await Promise.all([getRuntimeStatus(), getUserAiSettings()])
    status.value = runtime
    applySettings(settings)
  } catch (err) {
    error.value = readError(err, '运行状态读取失败')
  } finally {
    loading.value = false
  }
}

function applySettings(settings: UserAiSettings) {
  userSettings.value = settings
  const availableCodes = new Set(settings.providers.map((provider) => provider.providerCode))
  const preferredCode = settings.providerCode && availableCodes.has(settings.providerCode)
    ? settings.providerCode
    : settings.providers.find((provider) => provider.configured)?.providerCode
      || settings.providers[0]?.providerCode
      || ''
  selectedProviderCode.value = preferredCode
  const provider = settings.providers.find((item) => item.providerCode === preferredCode)
  model.value = settings.providerCode === preferredCode && settings.model
    ? settings.model
    : provider?.defaultModel || ''
  models.value = []
}

function onProviderChange() {
  message.value = ''
  models.value = []
  const settings = userSettings.value
  const provider = selectedProvider.value
  model.value = settings?.providerCode === selectedProviderCode.value && settings.model
    ? settings.model
    : provider?.defaultModel || ''
  apiKey.value = ''
}

async function saveSecret() {
  if (!apiKey.value.trim()) {
    showWarning('请输入要保存的 API Key。')
    return
  }
  await runAction('save-secret', async () => {
    const providerCode = requireProvider()
    const selectedModel = model.value.trim() || selectedProvider.value?.defaultModel || ''
    if (!selectedModel) throw new Error('当前 Provider 没有默认模型，请先填写模型。')
    await saveUserProviderSecret(providerCode, { apiKey: apiKey.value })
    apiKey.value = ''
    await saveUserAiPreference({ providerCode, model: selectedModel })
    model.value = selectedModel
    await reloadUserSettings(providerCode)
    showSuccess(`API Key 已加密保存且不会回显；后续学习将使用 ${providerCode} · ${selectedModel}。`)
  })
}

async function deleteSecret() {
  await runAction('delete-secret', async () => {
    const providerCode = requireProvider()
    await deleteUserProviderSecret(providerCode)
    apiKey.value = ''
    models.value = []
    await reloadUserSettings(providerCode)
    showSuccess('个人 API Key 已删除；关联的模型偏好也已撤销。')
  })
}

async function testProvider() {
  await runAction('test-provider', async () => {
    const providerCode = requireProvider()
    const result = await testUserProvider(providerCode)
    if (result.success) {
      showSuccess(`连接成功：${result.model || '默认模型'}，耗时 ${result.latencyMs} ms。`)
    } else {
      showWarning(result.message || '连接测试失败。')
    }
  })
}

async function loadModels() {
  await runAction('load-models', async () => {
    const providerCode = requireProvider()
    const result = await getUserProviderModels(providerCode)
    models.value = result.models
    if (!model.value && result.models.length > 0) {
      model.value = result.models[0] || ''
    }
    showSuccess(result.models.length > 0
      ? `已读取 ${result.models.length} 个模型；也可以直接手工填写模型名。`
      : '上游未返回模型列表，仍可直接手工填写模型名。')
  })
}

async function savePreference() {
  const selectedModel = model.value.trim()
  if (!selectedModel) {
    showWarning('请输入或选择模型。')
    return
  }
  await runAction('save-preference', async () => {
    const providerCode = requireProvider()
    await saveUserAiPreference({ providerCode, model: selectedModel })
    await reloadUserSettings(providerCode)
    model.value = selectedModel
    showSuccess(`已切换到 ${selectedModel}，后续 AI 请求将使用该模型。`)
  })
}

async function reloadUserSettings(keepProviderCode: string) {
  const settings = await getUserAiSettings()
  userSettings.value = settings
  selectedProviderCode.value = settings.providers.some(
    (provider) => provider.providerCode === keepProviderCode,
  ) ? keepProviderCode : settings.providers[0]?.providerCode || ''
  const provider = settings.providers.find(
    (item) => item.providerCode === selectedProviderCode.value,
  )
  if (settings.providerCode === selectedProviderCode.value && settings.model) {
    model.value = settings.model
  } else if (!model.value) {
    model.value = provider?.defaultModel || ''
  }
}

async function runAction(name: string, operation: () => Promise<void>) {
  if (busy.value) return
  action.value = name
  error.value = ''
  message.value = ''
  try {
    await operation()
  } catch (err) {
    error.value = readError(err, '操作失败，请稍后再试。')
  } finally {
    action.value = ''
  }
}

function requireProvider() {
  if (!selectedProviderCode.value) {
    throw new Error('当前没有可用 Provider，请先由系统管理员启用可信 Provider。')
  }
  return selectedProviderCode.value
}

function showSuccess(text: string) {
  messageTone.value = 'success'
  message.value = text
}

function showWarning(text: string) {
  messageTone.value = 'warning'
  message.value = text
}

function readError(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback
}
</script>

<template>
  <section class="runtime-page">
    <div class="section-head">
      <div>
        <h1>运行状态与个人 AI</h1>
        <p>API Key 按登录用户隔离并在后端加密保存；浏览器不持久化，也不会回显完整 Key。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="loading || busy" @click="loadAll">
        {{ loading ? '检测中...' : '重新检测' }}
      </button>
    </div>

    <div v-if="error" class="notice danger" role="alert">{{ error }}</div>
    <div v-if="message" class="notice" :class="messageTone">{{ message }}</div>

    <section v-if="userSettings" class="personal-ai panel">
      <div class="section-head">
        <div>
          <h2>我的 Provider 与模型</h2>
          <p>Provider 地址由系统维护，个人只能填写自己的 Key 和选择模型，避免把后端变成任意网络代理。</p>
        </div>
        <span class="chip" :class="userSettings.secureStorageAvailable ? 'mint' : 'danger'">
          {{ userSettings.secureStorageAvailable ? 'AES-GCM 安全存储已启用' : '安全存储未启用' }}
        </span>
      </div>

      <div v-if="!userSettings.persistenceAvailable" class="notice warning">
        后端持久化未启用。请启用 MySQL 持久化后再保存个人 API Key。
      </div>
      <div v-else-if="!userSettings.secureStorageAvailable" class="notice warning">
        后端安全主密钥初始化失败，请检查密钥文件权限或显式主密钥配置；为避免明文落库，输入框已锁定。
      </div>

      <div class="settings-grid">
        <label class="setting-field">
          <span>Provider</span>
          <select
            v-model="selectedProviderCode"
            class="field"
            :disabled="busy"
            @change="onProviderChange"
          >
            <option
              v-for="provider in userSettings.providers"
              :key="provider.providerCode"
              :value="provider.providerCode"
            >
              {{ provider.providerName }}（{{ provider.providerCode }}）
            </option>
          </select>
        </label>

        <div class="provider-summary">
          <span class="tag" :class="selectedProvider?.configured ? 'mint' : 'warn'">
            {{ selectedProvider?.configured ? `Key ${selectedProvider.maskedApiKey}` : '尚未保存 Key' }}
          </span>
          <span class="tag">默认模型：{{ selectedProvider?.defaultModel || '未设置' }}</span>
          <span v-if="userSettings.providerCode === selectedProviderCode" class="tag mint">
            当前生效：{{ userSettings.providerCode }} · {{ userSettings.model }}
          </span>
          <span v-if="userSettings.providerCode" class="tag mint">
            后续学习将使用该配置
          </span>
        </div>

        <label class="setting-field full-row">
          <span>个人 API Key</span>
          <input
            v-model="apiKey"
            class="field"
            type="password"
            maxlength="4096"
            autocomplete="new-password"
            spellcheck="false"
            placeholder="仅本次提交保存在内存中；保存成功后立即清空"
            :disabled="busy || !userSettings.persistenceAvailable || !userSettings.secureStorageAvailable"
            @keyup.enter="saveSecret"
          />
        </label>

        <div class="action-row full-row">
          <button
            class="btn"
            type="button"
            :disabled="busy || !apiKey.trim() || !userSettings.persistenceAvailable || !userSettings.secureStorageAvailable"
            @click="saveSecret"
          >
            {{ action === 'save-secret' ? '加密保存中...' : '加密保存 Key' }}
          </button>
          <button
            class="btn ghost"
            type="button"
            :disabled="busy || !selectedProvider?.configured"
            @click="testProvider"
          >
            {{ action === 'test-provider' ? '测试中...' : '测试连接' }}
          </button>
          <button
            class="btn ghost danger-button"
            type="button"
            :disabled="busy || !selectedProvider?.configured"
            @click="deleteSecret"
          >
            {{ action === 'delete-secret' ? '删除中...' : '删除个人 Key' }}
          </button>
        </div>

        <label class="setting-field full-row">
          <span>模型</span>
          <div class="model-row">
            <input
              v-model="model"
              class="field"
              list="runtime-provider-models"
              maxlength="200"
              autocomplete="off"
              spellcheck="false"
              placeholder="选择列表中的模型，或直接输入任意模型名"
              :disabled="busy || !selectedProvider?.configured"
              @keyup.enter="savePreference"
            />
            <datalist id="runtime-provider-models">
              <option v-for="item in models" :key="item" :value="item" />
            </datalist>
            <button
              class="btn secondary"
              type="button"
              :disabled="busy || !selectedProvider?.configured"
              @click="loadModels"
            >
              {{ action === 'load-models' ? '读取中...' : '读取模型列表' }}
            </button>
            <button
              class="btn"
              type="button"
              :disabled="busy || !selectedProvider?.configured || !model.trim()"
              @click="savePreference"
            >
              {{ action === 'save-preference' ? '切换中...' : '保存并切换模型' }}
            </button>
          </div>
        </label>
      </div>
    </section>

    <div v-if="status" class="runtime-grid">
      <article class="runtime-card panel">
        <span class="chip mint">Study Service</span>
        <strong>{{ status.studyService }}</strong>
        <p>持久化：{{ status.persistenceEnabled ? 'MySQL 已启用' : 'Mock / 内存' }}</p>
        <p>缓存：{{ status.cacheEnabled ? 'Redis 配置已启用' : '未启用' }}</p>
      </article>

      <article class="runtime-card panel">
        <span class="chip" :class="status.redisReachable ? 'mint' : 'warn'">Redis</span>
        <strong>{{ status.redisReachable ? '连接正常' : '未连通' }}</strong>
        <p>未连通时课堂进度缓存会退化，但 MySQL 仍可工作。</p>
      </article>

      <article class="runtime-card panel">
        <span class="chip" :class="status.aiRealEnabled ? 'mint' : 'warn'">系统 AI Gateway</span>
        <strong>{{ providerText }}</strong>
        <p>{{ status.aiGatewayService }}</p>
        <p>登录用户优先使用个人配置；没有个人配置时安全回退到 Mock。</p>
      </article>
    </div>

    <section v-if="status" class="section">
      <div class="section-head">
        <div>
          <h2>可信 Provider 目录</h2>
          <p>Base URL 只由系统配置，个人设置不会修改它。</p>
        </div>
        <span class="chip">后台默认：{{ status.defaultProviderCode || 'mock' }}</span>
      </div>

      <div class="provider-list">
        <article v-for="provider in status.providers" :key="provider.providerCode" class="provider-row panel">
          <div>
            <strong>{{ provider.providerCode }}</strong>
            <p>{{ provider.protocol }} · {{ provider.defaultModel }}</p>
            <small>{{ provider.baseUrl }}</small>
          </div>
          <div class="keyword-row">
            <span class="tag" :class="provider.enabled ? 'mint' : 'warn'">
              {{ provider.enabled ? '系统调用已启用' : '仅个人 Key 可用' }}
            </span>
            <span class="tag" :class="provider.apiKeyConfigured ? 'mint' : 'warn'">
              {{ provider.apiKeyConfigured ? '系统 Key 已配置' : '系统 Key 未配置' }}
            </span>
          </div>
        </article>
      </div>
    </section>

    <div v-else-if="loading" class="panel loading-panel">
      <p class="empty-state">正在检测后端运行状态...</p>
    </div>
  </section>
</template>

<style scoped>
.runtime-page {
  display: grid;
  gap: 18px;
}

.notice {
  border: 1px solid #a7f3d0;
  border-radius: 18px;
  padding: 12px 14px;
  color: #166534;
  background: #f0fdf4;
  line-height: 1.6;
  font-weight: 800;
}

.notice.warning {
  border-color: #fde68a;
  color: #92400e;
  background: #fffbeb;
}

.notice.danger {
  border-color: #fecaca;
  color: var(--red);
  background: #fef2f2;
}

.personal-ai {
  padding: 20px;
  display: grid;
  gap: 18px;
}

.settings-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
  align-items: end;
}

.setting-field {
  display: grid;
  gap: 8px;
  color: var(--primary-strong);
  font-size: 13px;
  font-weight: 900;
}

.full-row {
  grid-column: 1 / -1;
}

.provider-summary {
  min-height: 48px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.model-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 10px;
}

.danger-button {
  color: var(--red) !important;
  border-color: #fecaca !important;
}

.runtime-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.runtime-card {
  min-height: 180px;
  padding: 20px;
  display: grid;
  align-content: start;
  gap: 10px;
}

.runtime-card strong {
  color: var(--primary-strong);
  font-size: 24px;
  line-height: 1.3;
  word-break: break-word;
}

.runtime-card p,
.provider-row p,
.provider-row small {
  margin: 0;
  color: var(--muted);
  line-height: 1.65;
}

.provider-list {
  display: grid;
  gap: 12px;
}

.provider-row {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
}

.provider-row strong {
  color: var(--primary-strong);
  font-size: 18px;
}

.provider-row small {
  display: block;
  margin-top: 4px;
  word-break: break-all;
}

.loading-panel {
  padding: 20px;
}

@media (max-width: 980px) {
  .runtime-grid,
  .settings-grid,
  .provider-row,
  .model-row {
    grid-template-columns: 1fr;
  }

  .full-row {
    grid-column: auto;
  }
}
</style>


