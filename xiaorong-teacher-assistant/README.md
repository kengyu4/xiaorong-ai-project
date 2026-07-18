# 小绒老师助教

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

## 启用真实 AI Provider

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

注意：只填 `api-key` 不会自动启用真实模型。必须同时满足：

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

Compose 会启动 5 个服务：**前端（nginx 提供 Vue 构建产物）**、后端应用、MySQL、Redis、RabbitMQ Management。前端容器占用宿主 `8088`，通过 nginx 反向代理 `/api`、`/internal` 到后端容器；后端只在容器内网 `8088` 提供服务，宿主调试口映射为 `18088`。

> **固定题库模板必须放在 `../interactive-quiz-demo/后端题目固定互动模版（小绒老师+白子同桌）.md`**。Compose 把 `../interactive-quiz-demo` 以只读方式挂载到后端容器 `/templates`。若该文件缺失，`StudyTemplateProvider` 会静默回退到内置的 Vue3 demo（5 节点），导致课程内容错误。

```bash
cd xiaorong-teacher-assistant
docker compose up -d --build
```

访问地址：

- 前端入口（用户访问）：http://localhost:8088
- 后端调试口（直连 API）：http://localhost:18088
- RabbitMQ 管理台：http://localhost:15672 （默认 `guest/guest`）
- MySQL：localhost:13306 （容器内网仍为 `mysql:3306`；宿主口用 13306 以避开本机已占用的 3306）
- Redis：localhost:6379

可通过环境变量覆盖：`MYSQL_ROOT_PASSWORD`、`RABBITMQ_DEFAULT_USER`、`RABBITMQ_DEFAULT_PASS`、`XIAORONG_STUDY_TEMPLATE_PATH`、`BAILIAN_API_KEY`、`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`、`ONEAPI_API_KEY`。

### 重新写库（模板内容变更后）

Compose 的 `mysql-data` 是具名卷，会跨容器重建保留。若模板内容变更或库里残留错误数据（例如旧的 Vue fallback 行），重新 seed：

```bash
# 1. 删除旧课程与讲义材料行
docker exec xiaorong-mysql mysql -uroot -p123456 -e "USE xiaorong_teacher; DELETE FROM ai_lesson_material WHERE course_id=1; DELETE FROM ai_course WHERE id=1;"
# 2. 清 Redis 缓存（避免返回旧材料）
docker exec xiaorong-redis redis-cli --scan --pattern "xiaorong:study:*" | ForEach-Object { docker exec xiaorong-redis redis-cli del $_ }
# 3. 重启后端，StudySchemaInitializer 会用真实模板重新 seed
docker compose restart app
```

真实模板 seed 后，`GET /api/study/courses` 应返回「后端面试固定题库」，`lessonCount=151`、`homeworkCount=8`。

## 下一步替换点

- `MockStudyService` 替换为 MySQL + Redis 实现：已完成，可通过 `xiaorong.persistence.enabled=true` 启用
- `MockAiGatewayService` 替换为真实 Provider 路由：已完成，可通过 `xiaorong.ai.real-enabled=true` 启用
- `OpenAiCompatibleAdapter` 接入 DeepSeek / 百炼 / OneAPI：已完成配置骨架和真实 HTTP 调用
- 后台生成课程材料接口接 RabbitMQ：已完成，发布 pending 任务并由监听器写 MySQL / 刷 Redis
- 学习记录落库到 `ai_study_record`：已完成持久化模式，`ask()` 自由提问会记录 `free_ask`
