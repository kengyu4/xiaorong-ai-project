# 用户 API Key 与模型切换实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 实现按登录用户隔离的 API Key AES-256-GCM 加密持久化、可信 Provider 模型查询与运行时模型切换，并移除浏览器明文 Key 存储。

**Architecture:** 系统 Provider 目录继续由 `XiaorongProperties` 和 `AiProviderRegistry` 管理；新增用户密钥存储、加密服务与用户偏好服务。统一动态 `AiGatewayService` 根据 `AuthContext` 选择用户真实 Provider 或 Mock，前端 Runtime 页面只通过用户级 API 操作脱敏状态。

**Tech Stack:** Java 17+/Spring Boot 3.2、Spring JDBC、MySQL、JUnit 5/Mockito、Vue 3、TypeScript、Node 22 test runner、Vite。

**实施状态：已完成（2026-07-16）。** 最终验证：`mvn -q test`、`mvn -q -DskipTests compile`、`npm test`、`npm run type-check`、`npm run build` 均退出码 0；安全扫描未发现浏览器明文 Key 持久化或 Key/Authorization 日志输出。

---

## 文件结构

### 后端新增

- `src/main/java/com/xiaorong/assistant/ai/secret/AiSecretCryptoService.java`：AES-GCM 加解密与可用性校验。
- `src/main/java/com/xiaorong/assistant/ai/user/UserAiStore.java`：用户密钥与偏好的存储边界。
- `src/main/java/com/xiaorong/assistant/ai/user/JdbcUserAiStore.java`：MySQL 实现。
- `src/main/java/com/xiaorong/assistant/ai/user/UserAiSchemaInitializer.java`：建表。
- `src/main/java/com/xiaorong/assistant/ai/user/UserAiSettingsService.java`：用户隔离、脱敏、Provider 校验、测试和模型列表。
- `src/main/java/com/xiaorong/assistant/ai/user/UserAiController.java`：`/api/user/ai/**`。
- `src/main/java/com/xiaorong/assistant/ai/user/UserAiDtos.java`：请求/响应 DTO。
- `src/main/java/com/xiaorong/assistant/ai/service/impl/DynamicAiGatewayService.java`：统一运行时路由。

### 后端修改

- `src/main/java/com/xiaorong/assistant/config/XiaorongProperties.java`：增加 `ai.secret-master-key`。
- `src/main/resources/application.yml`：引用 `XIAORONG_AI_SECRET_MASTER_KEY`。
- `src/main/java/com/xiaorong/assistant/ai/adapter/AiProviderAdapter.java`：增加模型列表能力。
- `src/main/java/com/xiaorong/assistant/ai/adapter/OpenAiCompatibleAdapter.java`：实现 `/models`。
- `src/main/java/com/xiaorong/assistant/ai/service/impl/MockAiGatewayService.java`：改为始终可用的具体委托。
- `src/main/java/com/xiaorong/assistant/ai/service/impl/ProviderRoutingAiGatewayService.java`：改为始终可用的系统配置委托。
- `src/main/java/com/xiaorong/assistant/common/GlobalExceptionHandler.java`：避免向客户端回传不受控上游异常。

### 后端测试

- `src/test/java/com/xiaorong/assistant/ai/secret/AiSecretCryptoServiceTest.java`
- `src/test/java/com/xiaorong/assistant/ai/user/UserAiSettingsServiceTest.java`
- `src/test/java/com/xiaorong/assistant/ai/service/impl/DynamicAiGatewayServiceTest.java`
- `src/test/java/com/xiaorong/assistant/ai/adapter/OpenAiCompatibleAdapterTest.java`

### 前端新增/修改

- `frontend/Forhaed/src/api/admin.ts`：用户 AI 设置 API。
- `frontend/Forhaed/src/api/types.ts`：用户设置、模型、测试 DTO。
- `frontend/Forhaed/src/api/legacyApiKeyCleanup.ts`：仅负责删除旧存储。
- `frontend/Forhaed/src/api/legacyApiKeyCleanup.test.ts`：Node 22 单元测试。
- `frontend/Forhaed/src/views/RuntimeStatusView.vue`：正式配置 UI。
- `frontend/Forhaed/src/main.ts`、`src/router/index.ts`、`src/components/RuntimeBadge.vue`：删除 helper 引入。
- 删除 `frontend/Forhaed/src/apikey-runtime-helper.js`。

### 文档修改

- `xiaorong-teacher-assistant/docs/mysql-schema.sql`
- `dev-docs/xiaorong-teacher-assistant-README.md`
- `dev-docs/AI智能刷题重构设计与接口文档.md`
- `dev-docs/待做任务清单.md`
- `dev-docs/README.md`

---

### Task 1: AES-GCM 密钥服务

**Files:**
- Create: `xiaorong-teacher-assistant/src/test/java/com/xiaorong/assistant/ai/secret/AiSecretCryptoServiceTest.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/secret/AiSecretCryptoService.java`
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/config/XiaorongProperties.java`
- Modify: `xiaorong-teacher-assistant/src/main/resources/application.yml`

- [x] **Step 1: 写失败测试**

测试定义以下 API：

```java
AiSecretCryptoService service = new AiSecretCryptoService(base64Key);
EncryptedSecret encrypted = service.encrypt("sk-user-secret");
assertThat(encrypted.ciphertext()).doesNotContain("sk-user-secret");
assertThat(service.decrypt(encrypted)).isEqualTo("sk-user-secret");
```

同时测试：相同明文两次密文不同、篡改密文失败、空/非 Base64/非 32 字节主密钥时 `available()` 为 false 且加密拒绝执行。

- [x] **Step 2: 运行并确认 RED**

```powershell
mvn -q -Dtest=AiSecretCryptoServiceTest test
```

预期：编译失败，`AiSecretCryptoService` 尚不存在。

- [x] **Step 3: 最小实现**

实现：

```java
public record EncryptedSecret(String ciphertext, String iv, String keyVersion) {}

public boolean available();
public EncryptedSecret encrypt(String plaintext);
public String decrypt(EncryptedSecret encrypted);
```

使用 `AES/GCM/NoPadding`、12 字节随机 IV、128-bit GCM tag、Base64 存储和 `v1` 版本。

- [x] **Step 4: 配置绑定**

在 `XiaorongProperties.Ai` 中增加：

```java
private String secretMasterKey;
```

并在 `application.yml` 增加：

```yaml
secret-master-key: ${XIAORONG_AI_SECRET_MASTER_KEY:}
```

- [x] **Step 5: 运行并确认 GREEN**

```powershell
mvn -q -Dtest=AiSecretCryptoServiceTest test
```

预期：全部通过。

---

### Task 2: 用户密钥与偏好持久化边界

**Files:**
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiStore.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/JdbcUserAiStore.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiSchemaInitializer.java`
- Create: `xiaorong-teacher-assistant/src/test/java/com/xiaorong/assistant/ai/user/UserAiSettingsServiceTest.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiSettingsService.java`

- [x] **Step 1: 写服务失败测试和内存 Fake Store**

测试内定义 `FakeUserAiStore implements UserAiStore`，覆盖：

```java
service.saveSecret(10001L, "deepseek", "sk-user-a");
UserAiSettings settings = service.settings(10001L);
assertThat(settings.providers().get(0).maskedApiKey()).isEqualTo("****er-a");
assertThat(settings.toString()).doesNotContain("sk-user-a");
```

并验证用户 `10002L` 看不到 `10001L` 的配置、替换 Key、删除 Key 清除同 Provider 偏好、未启用 Provider 被拒绝、没有 Store/主密钥时安全失败。

- [x] **Step 2: 运行并确认 RED**

```powershell
mvn -q -Dtest=UserAiSettingsServiceTest test
```

预期：编译失败，用户 AI 服务和 Store 尚不存在。

- [x] **Step 3: 定义存储接口**

```java
interface UserAiStore {
    Optional<UserProviderSecretRow> findSecret(long userId, String providerCode);
    List<UserProviderSecretRow> findSecrets(long userId);
    void upsertSecret(UserProviderSecretRow row);
    boolean deleteSecret(long userId, String providerCode);
    Optional<UserAiPreferenceRow> findPreference(long userId);
    void upsertPreference(UserAiPreferenceRow row);
    void deletePreference(long userId);
    void initSchema();
}
```

记录类型只保存密文、IV、版本、尾号和时间，不提供明文字段。

- [x] **Step 4: 实现用户设置服务**

服务使用 `ObjectProvider<UserAiStore>` 兼容持久化关闭场景，所有公共方法显式接收由控制器从 `AuthContext` 获得的 `userId`。实现 Provider 校验、Key 长度校验、尾号脱敏、偏好保存与删除。

- [x] **Step 5: 实现 JDBC Store 和建表初始化器**

使用参数化 SQL 创建并操作：

```sql
ai_user_provider_secret
ai_user_ai_preference
```

`JdbcUserAiStore` 和初始化器均使用：

```java
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
```

- [x] **Step 6: 运行并确认 GREEN**

```powershell
mvn -q -Dtest=UserAiSettingsServiceTest test
```

预期：全部通过。

---

### Task 3: 个人 AI 设置 API

**Files:**
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiDtos.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiController.java`
- Extend test: `xiaorong-teacher-assistant/src/test/java/com/xiaorong/assistant/ai/user/UserAiSettingsServiceTest.java`

- [x] **Step 1: 写失败测试**

测试服务返回对象序列化后不包含完整 Key，并定义期望 DTO：

```java
new SaveSecretRequest("sk-user-secret");
new SavePreferenceRequest("deepseek", "deepseek-chat");
```

- [x] **Step 2: 运行并确认 RED**

```powershell
mvn -q -Dtest=UserAiSettingsServiceTest test
```

预期：DTO/接口尚不存在。

- [x] **Step 3: 实现控制器**

实现：

```text
GET    /api/user/ai/settings
PUT    /api/user/ai/providers/{providerCode}/secret
DELETE /api/user/ai/providers/{providerCode}/secret
POST   /api/user/ai/providers/{providerCode}/test
GET    /api/user/ai/providers/{providerCode}/models
PUT    /api/user/ai/preference
```

每个方法第一步调用 `AuthContext.requireUserId()`，请求体不含 `userId`。

- [x] **Step 4: 运行并确认 GREEN**

```powershell
mvn -q -Dtest=UserAiSettingsServiceTest test
```

预期：通过，序列化响应不包含 Key。

---

### Task 4: Provider 模型列表与安全测试

**Files:**
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/adapter/AiProviderAdapter.java`
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/adapter/OpenAiCompatibleAdapter.java`
- Create: `xiaorong-teacher-assistant/src/test/java/com/xiaorong/assistant/ai/adapter/OpenAiCompatibleAdapterTest.java`
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/user/UserAiSettingsService.java`

- [x] **Step 1: 写失败测试**

使用 Spring `MockRestServiceServer` 或可注入 `RestClient.Builder` 验证：

- `/models` 使用 Bearer Key；
- 返回的 `data[].id` 排序去重；
- 测试调用使用用户偏好模型；
- 异常消息不包含 Key。

期望适配器 API：

```java
List<String> listModels(XiaorongProperties.Provider provider);
```

- [x] **Step 2: 运行并确认 RED**

```powershell
mvn -q -Dtest=OpenAiCompatibleAdapterTest test
```

预期：`listModels` 尚不存在。

- [x] **Step 3: 实现适配器能力**

`AiProviderAdapter` 提供默认空列表；OpenAI Compatible 实现 GET `{baseUrl}/models`，仅返回模型 ID。

- [x] **Step 4: 接入用户设置服务**

服务根据 Provider 协议选择适配器，构造仅当前调用使用的 Provider 副本并填入解密 Key；实现 `testProvider` 和 `listModels`。

- [x] **Step 5: 运行并确认 GREEN**

```powershell
mvn -q -Dtest=OpenAiCompatibleAdapterTest,UserAiSettingsServiceTest test
```

预期：全部通过。

---

### Task 5: 动态 AI Gateway

**Files:**
- Create: `xiaorong-teacher-assistant/src/test/java/com/xiaorong/assistant/ai/service/impl/DynamicAiGatewayServiceTest.java`
- Create: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/service/impl/DynamicAiGatewayService.java`
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/service/impl/MockAiGatewayService.java`
- Modify: `xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/ai/service/impl/ProviderRoutingAiGatewayService.java`

- [x] **Step 1: 写失败测试**

覆盖：

```java
// 有登录用户但无 Key -> mock
// 有登录用户和偏好 -> 用户 Provider + 用户模型
// 用户选择损坏 -> 抛安全错误，不用系统 Key
// 无用户上下文 + real-enabled=false -> mock
// 无用户上下文 + real-enabled=true -> 系统 Provider delegate
```

同时覆盖 `chat`、`structured` 和 `stream` 的路由边界。

- [x] **Step 2: 运行并确认 RED**

```powershell
mvn -q -Dtest=DynamicAiGatewayServiceTest test
```

预期：动态 Gateway 尚不存在。

- [x] **Step 3: 调整现有委托 Bean**

移除 Mock 和 Provider Routing 上的互斥 `@ConditionalOnProperty`；让二者始终作为具体类型 Bean 存在。

- [x] **Step 4: 实现主 Gateway**

`DynamicAiGatewayService` 标注：

```java
@Service
@Primary
```

它读取 `AuthContext.current()` 和 `UserAiSettingsService.resolveRuntimeProvider(userId)`；有用户配置时直接选择适配器调用，无配置时 Mock。无用户上下文时按 `xiaorong.ai.real-enabled` 在系统 Provider 和 Mock 间选择。

- [x] **Step 5: 运行并确认 GREEN**

```powershell
mvn -q -Dtest=DynamicAiGatewayServiceTest test
```

预期：全部通过。

- [x] **Step 6: 后端回归测试**

```powershell
mvn -q test
mvn -q -DskipTests compile
```

预期：全部测试和编译通过。

---

### Task 6: 前端旧 Key 清理与 API 类型

**Files:**
- Create: `frontend/Forhaed/src/api/legacyApiKeyCleanup.test.ts`
- Create: `frontend/Forhaed/src/api/legacyApiKeyCleanup.ts`
- Modify: `frontend/Forhaed/src/api/types.ts`
- Modify: `frontend/Forhaed/src/api/admin.ts`
- Modify: `frontend/Forhaed/package.json`

- [x] **Step 1: 写失败测试**

使用 Node 22 类型剥离运行器：

```typescript
import test from 'node:test'
import assert from 'node:assert/strict'
import { clearLegacyApiKeys } from './legacyApiKeyCleanup.ts'

test('removes every legacy api key without reading or uploading values', () => {
  const removed: string[] = []
  clearLegacyApiKeys({ removeItem: (key) => removed.push(key) })
  assert.deepEqual(removed, [
    'runtimeApiKey', 'apiKey', 'apikey', 'OPENAI_API_KEY', 'aiApiKey',
  ])
})
```

- [x] **Step 2: 运行并确认 RED**

```powershell
node --experimental-strip-types --test src/api/legacyApiKeyCleanup.test.ts
```

预期：模块不存在。

- [x] **Step 3: 最小实现并增加脚本**

实现纯函数，仅调用 `removeItem`，不调用 `getItem`。在 `package.json` 增加：

```json
"test": "node --experimental-strip-types --test src/**/*.test.ts"
```

- [x] **Step 4: 增加 API 类型与函数**

新增：`UserAiSettings`、`UserProviderSetting`、`ProviderTestResult`、`ProviderModelsResult`、`SaveSecretRequest`、`SavePreferenceRequest`，并在 `admin.ts` 中实现六个用户 API 调用。

- [x] **Step 5: 运行并确认 GREEN**

```powershell
npm test
npm run type-check
```

预期：单元测试和类型检查通过。

---

### Task 7: Runtime Vue 页面

**Files:**
- Modify: `frontend/Forhaed/src/views/RuntimeStatusView.vue`
- Modify: `frontend/Forhaed/src/main.ts`
- Modify: `frontend/Forhaed/src/router/index.ts`
- Modify: `frontend/Forhaed/src/components/RuntimeBadge.vue`
- Delete: `frontend/Forhaed/src/apikey-runtime-helper.js`

- [x] **Step 1: 移除 helper 引入并调用安全清理**

只在应用启动时调用：

```typescript
clearLegacyApiKeys(window.localStorage)
```

清理函数不读取旧值。

- [x] **Step 2: 实现页面状态与操作**

页面使用正式 Vue 状态实现：

- 加载系统 Runtime 与个人 AI 设置；
- Provider 选择；
- 临时 Key 输入；
- 保存后 `apiKey.value = ''`；
- 删除 Key；
- 测试连接；
- 获取模型列表；
- 保存偏好；
- 展示脱敏 Key 和当前模型；
- 所有按钮有 loading/error/success 状态。

- [x] **Step 3: 检查明文持久化已消失**

```powershell
Get-ChildItem -Recurse -File src | Select-String -Pattern 'setItem.*apiKey|runtimeApiKey|getItem.*apiKey'
```

预期：除清理常量和测试外无匹配。

- [x] **Step 4: 前端验证**

```powershell
npm test
npm run type-check
npm run build
```

预期：全部通过。

---

### Task 8: Schema、文档和最终验证

**Files:**
- Modify: `xiaorong-teacher-assistant/docs/mysql-schema.sql`
- Modify: `dev-docs/xiaorong-teacher-assistant-README.md`
- Modify: `dev-docs/AI智能刷题重构设计与接口文档.md`
- Modify: `dev-docs/待做任务清单.md`
- Modify: `dev-docs/README.md`

- [x] **Step 1: 更新数据库文档**

加入 `ai_user_provider_secret` 和 `ai_user_ai_preference`，字段与 JDBC 初始化 SQL 完全一致。

- [x] **Step 2: 更新后端使用文档**

记录：

```text
XIAORONG_AI_SECRET_MASTER_KEY=<Base64 32-byte key>
xiaorong.persistence.enabled=true
```

并说明 Key 不回显、每用户隔离、旧 `localStorage` 只删除不迁移。

- [x] **Step 3: 更新接口设计与待做任务清单**

在 API 文档加入六个用户接口；在 `待做任务清单.md` 增加本任务条目并标记已完成，列出测试证据和后续项（管理员角色守卫、主密钥轮换工具）。

- [x] **Step 4: 最终后端验证**

```powershell
mvn -q test
mvn -q -DskipTests compile
```

- [x] **Step 5: 最终前端验证**

```powershell
npm test
npm run type-check
npm run build
```

- [x] **Step 6: 安全扫描**

```powershell
Get-ChildItem -Recurse -File frontend\Forhaed\src,xiaorong-teacher-assistant\src\main | Select-String -Pattern 'localStorage.setItem.*api|apiKey.*println|Authorization.*println'
```

预期：无明文 Key 持久化或日志输出。

- [x] **Step 7: 核对需求清单**

逐条核对设计文档第 13 节验收标准，只有在所有命令最新运行且退出码为 0 后才能报告完成。

---

## 运行时回归修正（2026-07-16）

真实 `http://localhost:8088/admin/runtime` 复验发现两个配置层问题，已按 TDD 修正：

- 当 `XIAORONG_AI_SECRET_MASTER_KEY` 为空时，后端改为读取或生成服务端持久化主密钥文件；Docker Compose 使用独立 `ai-secret-data` 命名卷，容器重启后复用，POSIX 权限为 `0600`。显式主密钥无效时仍失败关闭，不静默回退。
- 个人 Provider 选择改为使用全部预设可信目录；`enabled` 只控制系统/后台任务是否使用系统 Key，不再错误过滤 DeepSeek、OneAPI 等个人配置项。
- 已在真实 8088 实例完成临时 Key 保存、响应脱敏、Provider/模型偏好切换、删除清理和容器重启复验。
