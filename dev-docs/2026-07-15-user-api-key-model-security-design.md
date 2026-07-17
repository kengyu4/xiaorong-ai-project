# 用户 API Key 与模型切换安全设计

> 日期：2026-07-15  
> 状态：已确认  
> 适用项目：`xiaorong-teacher-assistant`、`frontend/Forhaed`

## 1. 背景

当前 `/admin/runtime` 页面通过 `apikey-runtime-helper.js` 将 API Key 明文写入浏览器 `localStorage`，并由前端直接携带 Key 检测 Provider。该方式存在以下问题：

1. 页面脚本、浏览器扩展或 XSS 可以直接读取明文 Key。
2. Key 只存在浏览器，后端课程、提问和批改流程无法可靠使用。
3. Provider 与默认模型主要由启动配置决定，保存后不能按当前登录用户动态切换。
4. 当前运行状态页面混合了个人 AI 配置和系统管理职责。
5. 现有 `xiaorong.ai.real-enabled` 在应用启动时决定注入 Mock 或真实 Gateway，不能满足运行时按用户切换。

本设计将 API Key 改为服务端按用户加密持久化，并让每个用户独立选择可信 Provider 和模型。

## 2. 目标

- 每个登录用户可以配置自己的 Provider API Key。
- API Key 不在浏览器持久化、不在接口响应中回显、不进入日志。
- API Key 使用 AES-256-GCM 加密后存入 MySQL，应用重启后仍可使用。
- 用户数据按当前认证会话中的 `userId` 隔离，接口不接受客户端传入的 `userId`。
- 用户只能选择系统预设的可信 Provider/Base URL，避免 SSRF。
- 用户可以获取 Provider 模型列表、手动填写模型 ID，并随时切换个人默认模型。
- 未配置个人 Key 时保留现有 Mock 体验；配置后无需重启即可使用真实模型。
- 管理员只能管理 Provider 目录和查看系统状态，不能读取用户 Key 明文。

## 3. 非目标

- 本阶段不允许普通用户配置任意 API Base URL。
- 本阶段不实现按课程、场景或角色分别配置模型，只保存一个用户级默认 Provider/模型。
- 本阶段不实现密钥共享、组织级密钥或额度计费。
- 本阶段不提供 API Key 明文找回功能；用户只能替换或删除。
- 本阶段不自动上传旧版 `localStorage` 中的 Key。

## 4. 总体架构

```mermaid
flowchart TD
    U[登录用户] --> UI[个人 AI 配置页面]
    UI -->|仅保存时提交一次 Key| API[/api/user/ai/**]
    API --> AUTH[从 AuthContext 获取 userId]
    AUTH --> CRYPTO[AES-256-GCM 加密服务]
    CRYPTO --> DB[(MySQL 密文)]

    REQ[课程/提问/批改请求] --> GATEWAY[动态 AI Gateway]
    GATEWAY --> PREF[读取用户 Provider/模型偏好]
    PREF --> SECRET[读取用户密文]
    SECRET --> CRYPTO2[仅在服务端内存解密]
    CRYPTO2 --> PROVIDER[可信 Provider Base URL]
    GATEWAY -->|未配置个人 Key| MOCK[Mock AI]
```

职责拆分：

- Provider 目录：系统级配置，包含代码、名称、协议、固定 Base URL、默认模型和启用状态。
- 用户密钥：用户级数据，仅包含加密 Key 及脱敏元数据。
- 用户偏好：用户级默认 Provider 和模型。
- 动态 Gateway：运行时根据当前用户配置决定走真实 Provider 或 Mock。

## 5. 数据模型

### 5.1 `ai_user_provider_secret`

```sql
create table if not exists ai_user_provider_secret (
  id bigint primary key auto_increment,
  user_id bigint not null,
  provider_code varchar(50) not null,
  encrypted_api_key text not null,
  encryption_iv varchar(64) not null,
  key_version varchar(20) not null,
  api_key_last_four varchar(8),
  create_time datetime not null,
  update_time datetime not null,
  unique key uk_ai_user_provider_secret (user_id, provider_code),
  key idx_ai_user_provider_secret_user (user_id)
);
```

- 同一用户对同一 Provider 仅保存一份 Key。
- `encrypted_api_key` 和 `encryption_iv` 使用 Base64 编码。
- `api_key_last_four` 仅用于展示脱敏状态，不参与认证或解密。
- 不设置可返回明文的字段。

### 5.2 `ai_user_ai_preference`

```sql
create table if not exists ai_user_ai_preference (
  user_id bigint primary key,
  provider_code varchar(50) not null,
  model varchar(120) not null,
  update_time datetime not null
);
```

本阶段保存一个用户级默认 Provider/模型。未来如需按场景路由，可再扩展 `scene` 字段和联合唯一键，不在当前范围提前实现。

## 6. 密钥加密设计

### 6.1 算法

- 算法：AES/GCM/NoPadding。
- 密钥长度：256 bit。
- IV：每次保存随机生成 12 字节。
- 认证标签：由 GCM 输出包含在密文中。
- 字符编码：UTF-8。
- 数据库存储：Base64 密文和 Base64 IV。

### 6.2 主密钥

主密钥支持显式注入和服务端文件两种来源，优先级如下：

```text
XIAORONG_AI_SECRET_MASTER_KEY
XIAORONG_AI_SECRET_MASTER_KEY_FILE
```

- 显式环境变量非空时，必须是 Base64 编码的 32 字节随机值，并优先使用。显式值格式错误时失败关闭，不允许静默生成另一把密钥。
- 显式环境变量为空时，从 `secret-master-key-file` 读取；文件不存在则由服务端生成 32 字节随机值并持久化。POSIX 文件权限设为 `0600`，文件内容不进入响应或日志。
- Docker Compose 将默认密钥文件放入独立命名卷 `ai-secret-data`，保证容器重建/重启后复用同一把主密钥。

安全存储启用条件：

1. `xiaorong.persistence.enabled=true`；
2. MySQL 可连接；
3. 显式主密钥有效，或主密钥文件可以安全读取/创建且内容有效。

任一条件不满足时，Mock 学习功能继续可用，用户密钥接口返回“安全存储未启用”，且禁止降级到浏览器存储、进程内明文存储或数据库明文存储。

### 6.3 密钥版本

首版写入 `key_version=v1`。解密时按版本选择主密钥，为未来轮换预留边界。当前阶段不实现在线批量轮换工具。

## 7. API 设计

所有个人接口必须通过认证，并从 `AuthContext.requireUserId()` 获取用户 ID。

### 7.1 查询个人 AI 设置

```http
GET /api/user/ai/settings
```

响应仅包含当前 Provider/模型，以及各 Provider 的 `configured`、`maskedApiKey`、`enabled` 等安全字段，不包含密文、IV、主密钥或完整 API Key。

### 7.2 保存或替换个人 Key

```http
PUT /api/user/ai/providers/{providerCode}/secret
Content-Type: application/json

{
  "apiKey": "用户输入的完整 Key"
}
```

规则：

- Key 去除首尾空白后不能为空。
- `providerCode` 必须存在于系统 Provider 目录且已启用。
- 保存时生成新 IV 并覆盖旧密文。
- 响应只返回 `configured`、`maskedApiKey` 和更新时间。

### 7.3 删除个人 Key

```http
DELETE /api/user/ai/providers/{providerCode}/secret
```

删除 Key 后，如果该 Provider 正是当前偏好，则同时清除个人偏好，后续请求回退 Mock。

### 7.4 测试个人 Provider

```http
POST /api/user/ai/providers/{providerCode}/test
```

后端读取当前用户密文、解密后发起最小请求。响应包含成功状态、延迟、Provider、模型和简短回复，不包含 Key。

### 7.5 获取模型列表

```http
GET /api/user/ai/providers/{providerCode}/models
```

后端使用当前用户 Key 请求可信 Provider 的 `/models`。返回排序、去重后的模型 ID 列表。若 Provider 不支持模型列表，返回空列表和允许手动输入的标记。

### 7.6 保存模型偏好

```http
PUT /api/user/ai/preference
Content-Type: application/json

{
  "providerCode": "deepseek",
  "model": "deepseek-chat"
}
```

Provider 必须启用，当前用户必须已配置对应 Key，模型 ID 去除首尾空白后不能为空且长度受限。模型不强制来自最近一次列表，以兼容不支持 `/models` 的 Provider。

## 8. 运行时路由设计

移除“启动时只能创建 Mock 或真实 Gateway 二选一”的限制，改为统一动态路由服务：

1. 从 `AuthContext` 获取当前用户；内部异步任务无用户上下文时继续使用系统配置或 Mock。
2. 查询用户偏好。
3. 校验对应 Provider 已启用。
4. 查询该用户对应 Provider 的密文。
5. 在调用边界内解密 Key。
6. 请求模型优先使用业务请求显式模型，否则使用用户偏好模型，再回退 Provider 默认模型。
7. 请求完成后不缓存、不返回明文 Key。
8. 用户无偏好或无 Key时走 Mock。
9. 用户已选择但配置损坏时返回明确错误，禁止静默使用其他用户或管理员 Key。

Provider 测试与课程业务调用复用同一适配器，但分别使用用户级和系统级配置对象，防止串用 Key。

## 9. 前端设计

### 9.1 页面功能

现有 `/admin/runtime` 保留作为兼容入口，页面主体改为正式 Vue 组件：

- Provider 选择；
- 密码型 API Key 输入框；
- 已配置状态和脱敏尾号；
- 保存并检测；
- 删除 Key；
- 刷新模型列表；
- 模型下拉框和自定义模型 ID；
- 设为当前模型；
- 当前生效 Provider/模型展示。

### 9.2 前端密钥规则

- Key 只存在于输入框临时状态。
- 请求结束后清空输入值。
- 不写入 `localStorage`、`sessionStorage`、URL、路由参数或错误提示。
- 不预填、不回显旧 Key。
- 页面加载时删除旧版遗留键：`runtimeApiKey`、`apiKey`、`apikey`、`OPENAI_API_KEY`、`aiApiKey`。
- 旧 Key 只删除，不自动上传。
- 删除 `apikey-runtime-helper.js` 的引入与业务逻辑。

## 10. 权限边界

- `/api/user/ai/**`：所有已登录用户可访问，但只能访问自己的数据。
- `/api/admin/**`：系统管理接口，后续统一增加 `admin` 角色校验。
- 当前任务中的个人 Key 功能不依赖 admin 角色。
- 普通用户不能修改 Provider Base URL、协议和启用状态。
- 管理员也不能通过 API 获取用户 Key 明文。

## 11. 安全与错误处理

- 禁止日志记录保存 Key 的请求体。
- Provider 上游错误只返回整理后的状态，不返回请求 Header。
- Provider Base URL 来自服务端可信配置，用户无权修改。
- 模型 ID 限长并拒绝控制字符。
- 数据库密文被修改时，AES-GCM 解密必须失败。
- 部署环境必须使用 HTTPS；本地 `localhost` 开发可使用 HTTP。

服务端应用拥有主密钥时仍可解密用户 Key，因此该方案主要防御浏览器泄露、日志泄露和数据库单独泄露，不能防御已完全控制应用进程与主密钥的攻击者。

## 12. 兼容性与迁移

- 不读取或迁移浏览器旧 Key，只删除遗留存储。
- `application.yml` 中所有预设 Provider 继续作为个人设置的可信目录；`enabled` 仅控制系统/后台任务使用系统 Key，不再过滤个人 Provider 下拉框。
- 系统级环境变量 Key 可保留给后台任务，但不得作为普通用户请求的默认兜底。
- 未启用安全存储时继续走 Mock。
- `/api/admin/runtime/status` 继续用于系统状态；个人配置状态由 `/api/user/ai/settings` 提供。

## 13. 测试与验收标准

### 13.1 后端

- AES-GCM 正确加解密，同一明文两次加密产生不同密文。
- 密文或 IV 被篡改时解密失败。
- 主密钥缺失、格式错误或长度错误时拒绝保存。
- 用户 A 无法读取、修改、测试或删除用户 B 的 Key。
- 设置、保存和错误响应中不出现完整 Key。
- 替换、删除 Key 后运行时行为正确。
- 用户模型偏好可持久化。
- 无 Key 时走 Mock；有 Key 时使用对应 Provider/模型。
- 模型列表与连接测试只使用当前用户 Key。

### 13.2 前端

- 页面加载清除旧版 Key 存储。
- 保存后输入框清空。
- 页面刷新只显示脱敏状态。
- 可以切换 Provider 和模型。
- 删除当前 Key 后回退 Mock。
- TypeScript 类型检查和 Vite 构建通过。

### 13.3 文档

实现完成后同步更新：

- `dev-docs/xiaorong-teacher-assistant-README.md`
- `dev-docs/AI智能刷题重构设计与接口文档.md`
- `dev-docs/待做任务清单.md`
- `dev-docs/README.md`
- `xiaorong-teacher-assistant/docs/mysql-schema.sql`

## 14. 实施顺序

1. 用测试定义加密、隔离和路由行为。
2. 实现加密服务与持久化表。
3. 实现个人 AI 设置 API。
4. 重构动态 Gateway。
5. 实现模型列表和连接测试。
6. 替换 Runtime 前端页面并清除旧存储逻辑。
7. 完成全量测试、构建和文档同步。

## 实施结果（2026-07-16）

本设计已完成落地：每用户 AES-256-GCM 密钥存储、可信 Provider 限制、六个用户设置接口、动态 Gateway、模型列表与手工模型切换、浏览器旧 Key 删除和 `/admin/runtime` 配置界面均已实现。2026-07-16 真实 8088 实例复验后又修正两项运行时问题：无显式环境变量时使用持久化文件主密钥，个人 Provider 下拉框展示全部预设可信 Provider，而不是仅展示系统 `enabled=true` 项。最终后端测试/编译、前端测试/类型检查/生产构建和安全扫描全部通过。后续保留两项安全增强：管理员角色守卫、主密钥轮换工具。

