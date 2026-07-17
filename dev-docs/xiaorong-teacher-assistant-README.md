# 小绒老师助教

这是 HaoAi 重构方向的后端骨架项目。当前目标不是完整业务版，而是先把“能看到、能感受到”的核心学习流程跑起来。

## 当前实现

- Spring Boot 单体后端，端口 `8088`
- 已内置静态前端 Demo，访问根路径即可体验
- 统一返回结构：`{ code, message, data }`
- 固定题库互动模板已接入：`../interactive-quiz-demo/后端题目固定互动模版（小绒老师+白子同桌）.md`
- 默认模式不依赖 MySQL / Redis，直接从固定模板解析课程，方便快速 Demo
- 可切换为 MySQL + Redis 模式：课程材料落 `ai_lesson_material`，学习会话落 `ai_study_session`，答题记录落 `ai_study_record`
- 后台课程材料生成已接 RabbitMQ：接口发布 `GENERATE_LESSON_MATERIAL` 任务，监听器消费后重建 `ai_lesson_material` 并刷新 Redis
- 小绒老师、白子同桌、岚川面试官的业务 prompt 常量
- AI Provider 路由已接入 OpenAI 兼容协议，可配置 DeepSeek / 百炼 / OneAPI
- 已实现当前用户学习概览：`/app/courses` 展示最高频薄弱标签和薄弱标签数量，无记录时显示“暂无”
- 已实现可见 AI 学习闭环：所有课堂节点最近 3 轮自由提问、低分异步深度讲评、首页/复盘个性化建议、POST SSE、安全运行元数据和按用户/日期 Token 预算；面试官完整业务暂缓
- 前端所需核心接口已经可调，后续可以直接换真实 service
- 前端已接入 `pic` 目录中的小绒老师、白子同桌图片资源
- UI 参考 Stitch 项目 `次元智学助手` 和 `docs/首页二次元风-PRD.md` 的低饱和 2.5D 学习风格

## 启动

```bash
mvn spring-boot:run
```

如果本机没有 Maven，也可以用 Gradle：

```bash
gradle bootRun
```

访问：

```text
http://localhost:8088
```

微服务网关与 Nacos 配置说明见：

```text
docs/microservice-nacos-gateway.md
```

前端体验路径：

```text
http://localhost:8088/
```

当前前端流程：

```text
首页课程列表
  -> 创建学习会话
  -> AI 课堂讲课
  -> 课堂提问
  -> 白子同桌互助
  -> 课后作业
  -> 学习复盘
```

固定模板适配结论：

- 适合当前前端，不需要推翻 UI。
- 模板中的“知识讲解”对应 `lecture` 节点。
- 模板中的“课堂提问”对应 `checkpoint` 节点。
- 模板中的“白子同桌课间请教”对应 `classmate` 节点。
- 最后的“布置作业”对应 `homework_intro` 和作业接口。
- 当前模板约 50 题，解析后约 151 个课堂节点，前端会按原有对话框逐段推进。

说明：

- Stitch 项目中可读取首页、课程详情、练习页等屏幕元数据和截图 URL。
- 当前环境下载 Stitch 远程图片时网络受限，所以 Demo 优先使用本地 `pic` 角色资源。
- `styles.css` 中保留了 Stitch 课程封面资源位：`/assets/stitch/course-cover-study.png`，后续下载成功后直接放入该路径即可自动显示。

## 核心接口

### 登录 / 注册

登录、注册会写入 MySQL 用户表 `ai_user`，密码使用 BCrypt 哈希保存。登录成功后返回随机 token，并把 token 会话写入 Redis。

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "student01",
  "password": "123456",
  "nickname": "同学"
}
```

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "student01",
  "password": "123456"
}
```

返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "xr_xxx",
    "user": {
      "userId": 1,
      "username": "student01",
      "nickname": "同学",
      "roles": ["student"]
    }
  }
}
```

登录后的接口需要携带：

```http
Authorization: Bearer xr_xxx
```

```http
GET /api/auth/me
POST /api/auth/logout
```

说明：

- 参考了 `hao-topic-master` 的“登录后 token + Redis 校验”流程。
- 没有照搬原项目的弱 JWT 密钥和日志打印 token 做法。
- 当前实现使用 Redis opaque token：退出登录会删除 Redis 会话，接口每次从 Redis 校验 token。
- `/api/auth/login`、`/api/auth/register` 放行，其余 `/api/**`、`/internal/**` 默认需要登录。
- 学习会话会校验 `session.user_id == 当前登录 userId`，不能拿别人的 sessionId 访问学习记录。

### 学习方向

```http
GET /api/study/courses?subjectId=1
```

### 创建学习会话

```http
POST /api/study/sessions
Content-Type: application/json

{
  "courseId": 1,
  "mode": "immersive"
}
```

### 获取课程脚本

```http
GET /api/study/sessions/{sessionId}/script
```

### 提交课堂提问

```http
POST /api/study/sessions/{sessionId}/nodes/n2/submit
Content-Type: application/json

{
  "answerText": "因为基本类型不能直接被 Proxy 代理，所以 ref 用 .value 包装"
}
```

### 提交同桌互助

```http
POST /api/study/sessions/{sessionId}/classmate/n4/submit
Content-Type: application/json

{
  "answerText": "解构后可能断开响应式连接，可以用 toRefs 保留"
}
```

### 举手提问

```http
POST /api/study/sessions/{sessionId}/ask
Content-Type: application/json

{
  "nodeId": "n2",
  "question": "ref 在模板里为什么不用 .value？"
}
```

### 自由对话、用户概览与 Token 预算

```http
GET  /api/study/overview
GET  /api/study/overview/advice
GET  /api/study/ai/budget
POST /api/study/sessions/{sessionId}/ask/stream
GET  /api/study/sessions/{sessionId}/ai-reviews/{taskId}
POST /api/study/sessions/{sessionId}/interview/follow-up
```

- `/overview` 只聚合当前登录用户的学习记录，返回 `topWeakTag`、`weakTags`、`weakTagCount`、`completedCount`；`/overview/advice` 返回 2～3 条建议、平均分、课程和安全运行元数据。
- `/ask/stream` 使用 POST JSON + Authorization，SSE 事件为 `delta`、`done`；缺少 `done`、无正文或流失败时，前端回退普通 `/ask`。
- 节点或作业评分低于 70 分时返回 `aiReviewTaskId`，前端最长轮询 60 秒并支持失败/超时重试。
- 单次输出预算：自由提问 1000、深度讲评 800、面试追问 600（4.3 完整业务暂缓）、个性化复盘 700 token。所有 AI 响应只公开 `providerCode`、`model`、`degraded`，不返回 Secret。

### 作业

```http
GET /api/study/sessions/{sessionId}/homework
```

```http
POST /api/study/sessions/{sessionId}/homework/101/submit
Content-Type: application/json

{
  "answerText": "ref 适合基本类型，script 中用 .value；reactive 适合对象，基于 Proxy，解构后可用 toRefs。"
}
```

### 复盘

```http
GET /api/study/sessions/{sessionId}/review
```

复盘响应中的 `nextActions` 来自当前用户个性化建议解析结果，并携带安全的 Provider/模型/降级状态；AI 不可用时返回固定兜底建议。

## AI 与后台接口

```http
GET  /api/admin/ai/prompts
POST /api/admin/ai/course/{courseId}/generate
GET  /api/admin/ai/material/{materialId}
POST /api/admin/ai/providers
POST /api/admin/ai/providers/{providerCode}/test
POST /internal/ai/chat
POST /internal/ai/structured
POST /internal/ai/chat/stream
```

课程材料生成流程：

- `POST /api/admin/ai/course/{courseId}/generate` 返回 `pending` 任务信息（含 `taskId`、`materialId`），先写入一条 `ai_lesson_material.status=pending`，再把任务投递到直连交换机 `xiaorong.ai.direct`。
- 队列 `xiaorong.ai.lesson-material.generate` 通过路由键 `GENERATE_LESSON_MATERIAL` 消费任务。
- 消费端复用 `StudyTemplateProvider.loadMaterials()` 生成课程材料，按与启动初始化相同的 SHA-256 内容哈希更新同一条 `ai_lesson_material` 为 `generated`，并调用 `StudyMaterialCache.put()` 刷新 Redis；失败时记录 `status=failed` 和 `error_msg`。
- `GET /api/admin/ai/material/{materialId}` 直接读取 MySQL `ai_lesson_material` 的真实状态，不再返回 mock 数据。

`POST /api/study/sessions/{sessionId}/ask` 在 MySQL + Redis 模式下会把自由提问落入 `ai_study_record`：`node_type=free_ask`、问题写入 `user_answer`、AI 回答写入 `feedback`、`score=0`、命中/未命中关键词为空数组、`bond_delta=0`。流式提问接口不做落库，避免在未完整捕获流内容时写入半截回答。

## 启用 MySQL + Redis

默认配置保持快速 Demo：

```yaml
xiaorong:
  persistence:
    enabled: false
  cache:
    enabled: false
```

要启用真实持久化，可以手动创建数据库：

```sql
create database xiaorong_teacher default character set utf8mb4 collate utf8mb4_unicode_ci;
```

完整 SQL 文件在：

```text
docs/mysql-schema.sql
```

也可以不手动建库。当前后端会在 `xiaorong.persistence.enabled=true` 时自动执行：

```sql
create database if not exists xiaorong_teacher;
create table if not exists ...
```

然后改配置：

```yaml
xiaorong:
  persistence:
    enabled: true
    seed-on-startup: true
    jdbc-url: jdbc:mysql://localhost:3306/xiaorong_teacher?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your_password
  cache:
    enabled: true
    prefix: "xiaorong:"
    ttl-seconds: 3600
  rabbitmq:
    lesson-material-exchange: xiaorong.ai.direct
    lesson-material-queue: xiaorong.ai.lesson-material.generate
    lesson-material-routing-key: GENERATE_LESSON_MATERIAL
```

RabbitMQ 连接配置：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

启动后会自动建表，并把固定题库模板写入：

```text
ai_course
ai_lesson_material
ai_study_session
ai_study_record
ai_provider_config
```

## 个人 API Key 与模型切换

登录用户可以在前端 `http://localhost:8088/admin/runtime` 配置自己的 API Key，并在可信 Provider 下切换模型。该页面虽然沿用 `/admin/runtime` 路径，但当前功能属于个人设置：后端始终从登录态 `AuthContext` 获取 `userId`，请求体不接受用户编号。

启用安全持久化需要开启 MySQL 持久化，并提供可用的服务端主密钥。主密钥来源按以下优先级处理：

```yaml
xiaorong:
  persistence:
    enabled: true
  ai:
    secret-master-key: ${XIAORONG_AI_SECRET_MASTER_KEY:}
    secret-master-key-file: ${XIAORONG_AI_SECRET_MASTER_KEY_FILE:${user.home}/.xiaorong/ai-secret-master.key}
```

1. `XIAORONG_AI_SECRET_MASTER_KEY` 非空时必须是 **32 字节随机值的 Base64 编码**，并优先使用；如果显式值无效，系统会失败关闭，不会悄悄改用另一把密钥。
2. 显式主密钥为空时，后端从 `secret-master-key-file` 读取主密钥；文件不存在则生成 32 字节随机值并持久化。POSIX 文件系统上权限设为 `0600`。
3. Docker Compose 默认将 `/app/data/ai-secret-master.key` 放入独立命名卷 `ai-secret-data`，容器重建或重启后继续复用，不需要把主密钥放到前端或 Git。
4. 生产环境仍优先推荐通过密钥管理服务/部署环境变量注入，或使用权限受控且具备备份策略的持久化密钥文件。

安全边界：

- API Key 按 `user_id + provider_code` 隔离，使用 AES-256-GCM 和随机 12 字节 IV 加密后写入 `ai_user_provider_secret`。
- 浏览器只在密码输入框中短暂持有明文；保存成功立即清空，不写入 `localStorage`、`sessionStorage`、Cookie 或 Pinia 持久化。
- 查询接口只返回 `****` 加末四位的脱敏值，不返回明文、密文、IV 或主密钥。
- 旧版浏览器本地 Key 会在启动时直接删除，不读取、不上传、不迁移。
- 用户不能填写 Base URL；可选择后端 `xiaorong.ai.providers` 中预设的全部可信 Provider。Provider 的 `enabled` 只控制系统/后台任务是否启用，不再限制用户使用自己的 Key。
- 模型可以从上游 `/models` 列表选择，也可以手工填写模型名；保存 Secret 时前端会同步保存当前 Provider/模型偏好，保存后该登录用户的后续 AI 请求使用所选模型。
- 用户已有加密 Secret 但偏好缺失或失效时，后端按可信 Provider 优先级选择第一个已配置项及其默认模型，避免静默进入 Mock。登录用户完全没有个人 Secret 或安全存储不可用时才回退 Mock，并且不会借用系统 API Key。系统 Provider 只供无用户上下文的后台任务按 `xiaorong.ai.real-enabled` 使用。

个人设置接口：

```http
GET    /api/user/ai/settings
PUT    /api/user/ai/providers/{providerCode}/secret
DELETE /api/user/ai/providers/{providerCode}/secret
POST   /api/user/ai/providers/{providerCode}/test
GET    /api/user/ai/providers/{providerCode}/models
PUT    /api/user/ai/preference
```

数据库表：

```text
ai_user_provider_secret
ai_user_ai_preference
```

> 主密钥文件或显式主密钥丢失后无法解密现有个人 Key。请备份 Docker 命名卷或生产密钥；当前实现记录 `key_version=v1`，后续需要补充可审计的主密钥轮换工具。
## 启用系统/后台真实 AI Provider

默认仍走 mock，适合固定题库省 token：

```yaml
xiaorong:
  ai:
    real-enabled: false
```

需要真实模型时：

```yaml
xiaorong:
  ai:
    real-enabled: true
    default-provider-code: bailian
    providers:
      - provider-code: bailian
        provider-name: 阿里百炼
        protocol: openai-compatible
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${BAILIAN_API_KEY:${DASHSCOPE_API_KEY:}}
        default-model: qwen-plus
        enabled: true
```

注意：以下条件只适用于 `application.yml` 或环境变量配置的系统/后台 Provider，不适用于用户在 `/admin/runtime` 保存的个人 Key。系统配置只填 `api-key` 不会自动启用真实模型，必须同时满足：

- `xiaorong.ai.real-enabled: true`
- `xiaorong.ai.default-provider-code` 指向要用的 provider
- 对应 provider 的 `enabled: true`
- 对应 provider 的 `api-key` 不为空

百炼和 OneAPI 也走同一个 `OpenAiCompatibleAdapter`，只需要换 `base-url`、`api-key`、`default-model`。

也可以运行时提交 Provider 配置，保存后当前进程立即生效：

```http
POST /api/admin/ai/providers
Content-Type: application/json

{
  "providerCode": "bailian",
  "providerName": "阿里百炼",
  "protocol": "openai-compatible",
  "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "apiKey": "你的百炼APIKey",
  "defaultModel": "qwen-plus",
  "supportStream": true,
  "supportJson": true,
  "priority": 10,
  "enabled": true
}
```

P0 阶段这个接口更新运行时路由；重启后仍以 `application.yml` / 环境变量为准。

测试百炼是否真的生效：

```http
POST /api/admin/ai/providers/bailian/test
```

可以用这个接口检查当前到底是不是 mock：

```http
GET /api/admin/runtime/status
```

## Docker 部署

项目内置 `Dockerfile`、`docker-compose.yml` 和 `.dockerignore`。Dockerfile 使用宿主机 Maven 构建的 JAR，避免容器构建阶段重复下载依赖；Compose 会启动前端、MySQL、Redis、RabbitMQ Management 与应用，并把 `../interactive-quiz-demo` 以只读方式挂载到容器 `/templates`：

```bash
cd xiaorong-teacher-assistant
mvn -q -DskipTests package
docker compose up -d --build
```

访问地址：

- 应用：http://localhost:8088
- RabbitMQ 管理台：http://localhost:15672 （默认 `guest/guest`）
- MySQL：localhost:3306
- Redis：localhost:6379

可通过环境变量覆盖：`MYSQL_ROOT_PASSWORD`、`RABBITMQ_DEFAULT_USER`、`RABBITMQ_DEFAULT_PASS`、`XIAORONG_STUDY_TEMPLATE_PATH`、`BAILIAN_API_KEY`、`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`、`ONEAPI_API_KEY`。单体模式默认在 bootstrap 阶段禁用 Nacos；微服务模式需设置 `SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=true` 和 `SPRING_CLOUD_NACOS_CONFIG_ENABLED=true`。

## 下一步替换点

- `MockStudyService` 替换为 MySQL + Redis 实现：已完成，可通过 `xiaorong.persistence.enabled=true` 启用
- `MockAiGatewayService` 替换为真实 Provider 路由：已完成，可通过 `xiaorong.ai.real-enabled=true` 启用
- `OpenAiCompatibleAdapter` 接入 DeepSeek / 百炼 / OneAPI：已完成配置骨架和真实 HTTP 调用
- 后台生成课程材料接口接 RabbitMQ：已完成，发布 pending 任务并由监听器写 MySQL / 刷 Redis
- 学习记录落库到 `ai_study_record`：已完成持久化模式，`ask()` 自由提问会记录 `free_ask`

### Free conversation streaming behavior

The classroom question endpoint uses POST SSE. Bailian uses Spring AI Alibaba `DashScopeChatModel.stream(Prompt)` and forwards every received text delta immediately; DeepSeek and OneAPI keep the OpenAI-compatible streaming adapter. The reverse proxy is configured with `proxy_buffering off`. If the provider stream fails, the UI receives a clearly degraded fallback response; API keys and upstream error bodies are never included in SSE events.

Free answers use the XiaoRong teacher system prompt: concise, conclusion-first, no filler, normally three to five sentences (within 180 Chinese characters).


### SSE delivery verification (2026-07-16)

- [x] Replaced completed-answer chunking with provider upstream SSE (`stream: true`) and incremental `delta` forwarding.
- [x] Added Nginx `/api/` SSE no-buffer configuration: `proxy_buffering off`, `proxy_cache off`, `proxy_read_timeout 3600s`, and `X-Accel-Buffering: no`.
- [x] Free conversation system prompt now enforces the XiaoRong teacher persona and concise conclusion-first answers (3–5 sentences, target 180 Chinese characters).
- [x] Verification passed: backend full `mvn -q test`; frontend `npm test` (12/12); frontend `npm run build`; backend package; rebuilt local Docker services.
- [x] Post-deploy check: `GET http://localhost:8088/` returns 200, and the authenticated runtime endpoint now reaches the backend (unauthenticated response is expected 401 rather than 502).


### Bailian native Spring AI stream (2026-07-16)

- Bailian configuration uses `protocol: dashscope` and native base URL `https://dashscope.aliyuncs.com`.
- The backend creates `DashScopeChatModel` per request from the current user's decrypted Key and selected model, then subscribes to `stream(Prompt)` without blocking the controller response.
- Browser delivery is `ready` ? `delta...` ? `done`; the frontend updates connection state on `ready` and appends each delta immediately.
- AI answer text is plain text. Markdown emphasis markers are removed safely even when `**` is split across different upstream chunks. API Key masking in runtime settings remains intentionally enabled.
- Docker verification on port 8088: frontend 200; protected runtime/courses endpoints 401 when unauthenticated, proving the proxy reaches the backend instead of returning 502.
