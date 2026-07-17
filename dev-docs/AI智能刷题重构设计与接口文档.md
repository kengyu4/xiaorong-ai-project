# AI 智能刷题重构设计与接口文档

## 1. 总体结论

推荐把项目重构为：

**AI 老师课堂提问为主，AI 同桌课间互助为辅，固定题库内容预生成，动态问答实时生成。**

产品主线：

```text
选择学习方向
  ↓
AI 老师讲课
  ↓
课堂中穿插提问
  ↓
AI 同桌课间请教
  ↓
布置作业
  ↓
提交一题，AI 讲评一题
  ↓
复盘薄弱点，推荐下一课
```

技术主线：

```text
前端 Vue3 / uni-app
  ↓
业务后端 SpringCloud
  ↓
AI 编排层 service-ai
  ↓
AI Provider Adapter
  ↓
OpenAI / 阿里云百炼 / DeepSeek / 智谱 / Kimi / 豆包 / 千帆 / 混元 / 中转站 / 本地模型
```

最重要的架构原则：

- 固定题库不要每次实时生成 AI 讲课内容。
- 题库更新后异步预生成讲课脚本、课堂提问、同桌台词、作业讲评模板。
- 用户学习时优先读 MySQL / Redis 缓存。
- 只有举手自由提问、低置信度批改、深度讲评才实时调用 AI。
- AI 接入层统一抽象，业务代码不直接依赖任何一家模型厂商。

当前 `xiaorong-teacher-assistant` 的落地状态：课程材料生成接口已通过 RabbitMQ 投递 `GENERATE_LESSON_MATERIAL` 任务，监听器复用固定模板解析与 SHA-256 内容哈希写入 `ai_lesson_material`，并刷新 Redis；普通举手自由提问 `ask()` 已按 `free_ask` 类型落入 `ai_study_record`，流式提问暂不落库以避免记录半截流内容。

## 2. 分阶段建设

### 2.1 P0：快速小 demo

目标：

最快验证“AI 老师 + 课堂提问 + AI 同桌”的学习体验。

必要骨架：

- 一个静态页面或 Vue 页面。
- 一个课程 JSON。
- 课程节点类型：`lecture`、`checkpoint`、`classmate`、`homework`、`review`。
- 本地关键词评分函数。
- Mock AI 讲评函数。
- 两个角色头像：AI 老师、AI 同桌。

P0 不需要：

- 不需要真实 AI 接口。
- 不需要 RabbitMQ。
- 不需要复杂角色成长。
- 不需要完整后台管理。
- 不需要接所有模型供应商。

推荐技术：

```text
前端：Vue3 + Vite 或直接静态 index.html
状态：本地 reactive / Pinia
数据：course.mock.json
评分：关键词 + 长度 + 结构完整度
部署：直接打开 HTML 或 npm run dev
```

### 2.2 P1：可用业务版

目标：

接入真实后端，支持课程材料预生成和用户学习记录。

必要能力：

- AI 课程材料表。
- 学习会话表。
- 课堂节点提交接口。
- 作业提交接口。
- Redis 缓存课程脚本。
- RabbitMQ 异步生成课程材料。
- 一个 OpenAI 兼容 Provider Adapter。
- 一个阿里云百炼或 DeepSeek Provider 配置。

推荐技术：

```text
前端：uni-app + Vue3 + uv-ui
后端：SpringBoot 3.2 + SpringCloud 2023
AI：Spring AI + 自定义 Provider Adapter
缓存：Redis
异步：RabbitMQ
数据库：MySQL + MybatisPlus
鉴权：SpringSecurity
```

### 2.3 P2：完整产品版

目标：

多模型接入、个性化学习、角色成长、内容审核、运营后台。

扩展能力：

- 多供应商模型配置。
- 按场景选择模型。
- AI 输出审查。
- 用户薄弱点画像。
- 复习计划。
- 同桌羁绊值。
- 角色皮肤。
- TTS 语音朗读。
- 后台课程材料审核。

## 3. 项目模块设计

### 3.1 前端模块

推荐新增页面：

```text
src/pages/study/course/index.vue          学习方向选择
src/pages/study/classroom/index.vue       AI 课堂
src/pages/study/homework/index.vue        课后作业
src/pages/study/review/index.vue          学习复盘
src/pages/study/role/index.vue            角色资料，可后置
```

推荐新增组件：

```text
components/study/DialogueBox.vue          游戏对话框
components/study/TeacherPanel.vue         AI 老师展示
components/study/ClassmatePanel.vue       AI 同桌展示
components/study/LessonNode.vue           课程节点渲染器
components/study/CheckpointQuestion.vue   课堂提问
components/study/HomeworkAnswer.vue       作业答题
components/study/AiReviewCard.vue         AI 讲评卡片
components/study/StudyProgress.vue        学习进度
components/study/BondMeter.vue            羁绊值
```

前端状态建议：

```ts
type StudyPhase =
  | 'choose'
  | 'lesson'
  | 'checkpoint'
  | 'classmate'
  | 'homework'
  | 'review'

interface StudyState {
  sessionId: number
  courseId: number
  phase: StudyPhase
  nodeIndex: number
  homeworkIndex: number
  currentNode?: LessonNode
  dialogueHistory: DialogueMessage[]
  userAnswer: string
  score?: number
  hitKeywords: string[]
  missKeywords: string[]
  bondValue: number
  weakTags: string[]
}
```

### 3.2 后端模块

保守改造方案：

```text
topic-api-ai
  负责 AI 调用、Prompt、Provider Adapter、课程材料生成

topic-api-topic
  负责题库、作业题、学习记录、答题记录

web-gateway
  统一对 H5 暴露接口
```

更清晰的长期方案：

```text
topic-api-study
  专门负责 AI 学习流程、课程会话、课堂节点、同桌互动、复盘推荐
```

推荐做法：

- P0/P1 阶段先不新增服务，减少改造成本。
- P2 阶段如果学习业务膨胀，再拆 `topic-api-study`。

后端核心包结构建议：

```text
com.hao.topic.ai
  adapter
    AiProviderAdapter.java
    OpenAiCompatibleAdapter.java
    AnthropicAdapter.java
    OllamaAdapter.java
  config
    AiProviderProperties.java
    AiModelRoutingProperties.java
  domain
    AiProviderConfig.java
    AiPromptTemplate.java
    AiRequestLog.java
  service
    AiGatewayService.java
    LessonMaterialGenerateService.java
    AiReviewService.java
  prompt
    LessonPromptBuilder.java
    ReviewPromptBuilder.java
    AskPromptBuilder.java

com.hao.topic.topic
  domain
    AiCourse.java
    AiLessonMaterial.java
    AiStudySession.java
    AiStudyRecord.java
    AiClassmateBond.java
  service
    StudyCourseService.java
    StudySessionService.java
    StudyHomeworkService.java
```

## 4. 核心领域模型

### 4.1 课程 Course

```ts
interface AiCourse {
  id: number
  subjectId: number
  title: string
  description: string
  difficulty: 'easy' | 'medium' | 'hard'
  tags: string[]
  teacherPersonaId: number
  classmatePersonaId: number
  topicIds: number[]
  enabled: boolean
}
```

### 4.2 课程材料 LessonMaterial

```ts
interface AiLessonMaterial {
  id: number
  courseId: number
  contentHash: string
  promptVersion: string
  status: 'pending' | 'generated' | 'failed' | 'reviewed'
  script: LessonScript
}
```

### 4.3 课程节点 LessonNode

```ts
type LessonNodeType =
  | 'lecture'
  | 'checkpoint'
  | 'classmate'
  | 'homework_intro'
  | 'review'

interface LessonNode {
  id: string
  type: LessonNodeType
  speaker: 'teacher' | 'classmate' | 'system'
  title: string
  text: string
  knowledgePoint?: string
  question?: string
  answerKeywords?: string[]
  standardAnswer?: string
  explanation?: string
  reward?: {
    rightBond: number
    wrongBond: number
  }
}
```

### 4.4 学习会话 StudySession

```ts
interface StudySession {
  id: number
  userId: number
  courseId: number
  status: 'learning' | 'homework' | 'finished'
  currentNodeIndex: number
  currentHomeworkIndex: number
  totalScore: number
  bondValue: number
  weakTags: string[]
  startedAt: string
  finishedAt?: string
}
```

## 5. 数据库表设计

### 5.1 AI 课程表

```sql
create table ai_course (
  id bigint primary key auto_increment,
  subject_id bigint not null,
  title varchar(100) not null,
  description varchar(500),
  difficulty varchar(20),
  tags json,
  teacher_persona_id bigint,
  classmate_persona_id bigint,
  topic_ids json,
  enabled tinyint default 1,
  create_time datetime,
  update_time datetime
);
```

### 5.2 AI 课程材料表

```sql
create table ai_lesson_material (
  id bigint primary key auto_increment,
  course_id bigint not null,
  content_hash varchar(64) not null,
  prompt_version varchar(50) not null,
  script_json json,
  status varchar(20) default 'pending',
  error_msg varchar(1000),
  create_time datetime,
  update_time datetime,
  unique key uk_course_hash (course_id, content_hash, prompt_version)
);
```

### 5.3 AI 角色表

```sql
create table ai_persona (
  id bigint primary key auto_increment,
  name varchar(50) not null,
  role varchar(30) not null,
  avatar_url varchar(500),
  tone varchar(200),
  persona_prompt text,
  boundary_rule text,
  enabled tinyint default 1,
  create_time datetime,
  update_time datetime
);
```

### 5.4 学习会话表

```sql
create table ai_study_session (
  id bigint primary key auto_increment,
  user_id bigint not null,
  course_id bigint not null,
  status varchar(20) not null,
  current_node_index int default 0,
  current_homework_index int default 0,
  total_score int default 0,
  bond_value int default 0,
  weak_tags json,
  start_time datetime,
  finish_time datetime,
  create_time datetime,
  update_time datetime
);
```

### 5.5 学习记录表

```sql
create table ai_study_record (
  id bigint primary key auto_increment,
  session_id bigint not null,
  user_id bigint not null,
  course_id bigint not null,
  node_id varchar(80),
  node_type varchar(30),
  topic_id bigint,
  user_answer text,
  score int,
  hit_keywords json,
  miss_keywords json,
  feedback text,
  bond_delta int default 0,
  duration int,
  create_time datetime
);
```

### 5.6 AI 供应商配置表

```sql
create table ai_provider_config (
  id bigint primary key auto_increment,
  provider_code varchar(50) not null,
  provider_name varchar(80) not null,
  protocol varchar(30) not null,
  base_url varchar(500),
  api_key_encrypted varchar(1000),
  default_model varchar(100),
  support_stream tinyint default 1,
  support_json tinyint default 1,
  priority int default 100,
  enabled tinyint default 1,
  remark varchar(500),
  create_time datetime,
  update_time datetime
);
```

### 5.7 AI 请求日志表

```sql
create table ai_request_log (
  id bigint primary key auto_increment,
  user_id bigint,
  provider_code varchar(50),
  model varchar(100),
  scene varchar(50),
  prompt_tokens int,
  completion_tokens int,
  latency_ms int,
  success tinyint,
  error_msg varchar(1000),
  request_hash varchar(64),
  create_time datetime
);
```

## 6. AI 接入架构

### 6.1 统一接口

业务代码只调用 `AiGatewayService`，不直接调用模型厂商。

```java
public interface AiGatewayService {
    AiChatResponse chat(AiChatRequest request);

    void stream(AiChatRequest request, AiStreamHandler handler);

    <T> T structured(AiStructuredRequest<T> request, Class<T> responseType);
}
```

Provider 适配器：

```java
public interface AiProviderAdapter {
    String providerCode();

    boolean supports(AiProviderConfig config, AiScene scene);

    AiChatResponse chat(AiChatRequest request, AiProviderConfig config);

    void stream(AiChatRequest request, AiProviderConfig config, AiStreamHandler handler);
}
```

### 6.2 Provider 类型

推荐先支持三类协议：

| 协议 | 用途 | 示例 |
| --- | --- | --- |
| `openai-compatible` | 最优先，覆盖大量模型和中转站 | OpenAI、阿里云百炼兼容模式、DeepSeek、Kimi、智谱、OneAPI、NewAPI、LiteLLM |
| `spring-ai` | 复用 Spring AI 抽象 | Spring AI 已支持的 ChatModel |
| `custom` | 非兼容协议单独适配 | Anthropic Messages、特殊企业内网模型 |

### 6.3 推荐支持的供应商

| providerCode | 协议 | 适用场景 |
| --- | --- | --- |
| `openai` | OpenAI / Responses / Chat Completions | 高质量问答、结构化输出 |
| `dashscope` | OpenAI compatible / DashScope SDK | 阿里云百炼，适合国内部署 |
| `deepseek` | OpenAI compatible | 性价比高，适合批改和讲解 |
| `zhipu` | OpenAI compatible | 国内备用模型 |
| `moonshot` | OpenAI compatible | 长上下文场景 |
| `doubao` | OpenAI compatible / custom | 国内备用 |
| `qianfan` | OpenAI compatible / custom | 百度千帆 |
| `hunyuan` | OpenAI compatible / custom | 腾讯混元 |
| `oneapi` | OpenAI compatible relay | 多模型中转站 |
| `newapi` | OpenAI compatible relay | 多模型中转站 |
| `litellm` | OpenAI compatible relay | 自建统一中转 |
| `ollama` | custom / OpenAI compatible | 本地模型、离线 demo |

### 6.4 场景路由

不同场景可以用不同模型：

```yaml
ai:
  route:
    lesson-generate:
      provider: dashscope
      model: qwen-plus
      temperature: 0.4
    checkpoint-grade:
      provider: deepseek
      model: deepseek-chat
      temperature: 0.2
    free-ask:
      provider: openai
      model: gpt-4.1-mini
      temperature: 0.5
      stream: true
    fallback:
      provider: oneapi
      model: gpt-4o-mini
```

### 6.5 OpenAI 兼容请求格式

内部统一为：

```json
{
  "scene": "free-ask",
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是 AI 老师，只能围绕当前课程知识点回答。"
    },
    {
      "role": "user",
      "content": "reactive 解构为什么会丢响应式？"
    }
  ],
  "temperature": 0.4,
  "stream": false,
  "responseFormat": "json"
}
```

OpenAI 兼容适配器负责转换为供应商实际请求。

### 6.6 供应商失败回退

```text
业务请求
  ↓
AiGatewayService
  ↓
读取场景路由
  ↓
调用主 Provider
  ↓ 失败 / 超时 / 限流
调用备用 Provider
  ↓
记录 ai_request_log
```

回退策略：

- 超时：3 到 8 秒，按场景配置。
- 自由提问：可以流式输出，用户可等待更久。
- 课堂脚本生成：异步任务，可以重试。
- 课堂节点评分：必须快，优先规则评分。

## 7. token 和响应速度方案

### 7.1 预生成内容

这些内容在题库更新后异步生成：

- AI 老师讲课脚本。
- 课堂理解检测题。
- AI 同桌请教问题。
- 答对夸赞模板。
- 答错安慰模板。
- 标准解释。
- 作业讲评模板。
- 常见错误解释。

流程：

```text
题目新增 / 修改 / 专题更新
  ↓
计算 contentHash
  ↓
contentHash 变化
  ↓
发送 RabbitMQ：GENERATE_LESSON_MATERIAL
  ↓
service-ai 调用模型
  ↓
生成 script_json
  ↓
保存 MySQL
  ↓
写 Redis 热点缓存
```

### 7.2 实时 AI 场景

只在这些场景实时调用：

- 用户举手自由提问。
- 用户答案很长，规则评分低置信度。
- 用户点击“详细讲讲”。
- 复盘阶段生成个性化建议。

### 7.3 缓存 key

```text
ai:course:list:{subjectId}
ai:lesson:course:{courseId}
ai:lesson:node:{courseId}:{nodeId}
ai:homework:template:{topicId}
ai:persona:{personaId}
ai:provider:config
```

### 7.4 contentHash

```text
contentHash = sha256(
  courseTitle
  + topicTitle
  + topicAnswer
  + topicTags
  + promptVersion
)
```

如果 hash 没变，不重新生成。

## 8. 前端业务接口文档

统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 8.1 获取学习方向

```http
GET /api/study/courses?subjectId=1
```

响应：

```json
{
  "code": 200,
  "data": [
    {
      "courseId": 1,
      "title": "Vue3 响应式入门",
      "description": "理解 ref、reactive、computed、watch",
      "difficulty": "easy",
      "tags": ["Vue3", "响应式"],
      "lessonCount": 8,
      "homeworkCount": 3,
      "teacherAvatar": "/avatar/teacher.webp",
      "classmateAvatar": "/avatar/classmate.webp"
    }
  ]
}
```

### 8.2 创建学习会话

```http
POST /api/study/sessions
```

请求：

```json
{
  "courseId": 1,
  "mode": "immersive"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "sessionId": 10001,
    "courseId": 1,
    "status": "learning",
    "currentNodeIndex": 0
  }
}
```

### 8.3 获取课程脚本

```http
GET /api/study/sessions/{sessionId}/script
```

响应：

```json
{
  "code": 200,
  "data": {
    "courseId": 1,
    "title": "Vue3 响应式入门",
    "teacher": {
      "name": "小绒老师",
      "avatar": "/avatar/teacher.webp"
    },
    "classmate": {
      "name": "白子同桌",
      "avatar": "/avatar/classmate.webp",
      "bondValue": 0
    },
    "nodes": [
      {
        "nodeId": "n1",
        "type": "lecture",
        "speaker": "teacher",
        "title": "先建立地图",
        "text": "Vue3 响应式不是背 API，而是理解数据变化后视图为什么会更新。",
        "knowledgePoint": "响应式整体认知"
      },
      {
        "nodeId": "n2",
        "type": "checkpoint",
        "speaker": "teacher",
        "question": "number 类型为什么更适合用 ref？",
        "answerType": "short_text",
        "answerKeywords": ["基本类型", "Proxy", ".value"]
      }
    ]
  }
}
```

### 8.4 提交课堂提问

```http
POST /api/study/sessions/{sessionId}/nodes/{nodeId}/submit
```

请求：

```json
{
  "answerText": "因为基本类型不能直接被 Proxy 代理，所以 ref 用 value 包装"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "score": 88,
    "hitKeywords": ["基本类型", "Proxy", "value"],
    "missKeywords": [".value"],
    "feedback": "方向对了。面试时可以再补一句：script 中访问 ref 要用 .value。",
    "teacherReply": "答得不错，你已经抓住基本类型和 Proxy 的关系了。",
    "nextNodeIndex": 2
  }
}
```

### 8.5 提交 AI 同桌互助答案

```http
POST /api/study/sessions/{sessionId}/classmate/{nodeId}/submit
```

请求：

```json
{
  "answerText": "解构后拿到的是普通值，不再保留原来 reactive 对象上的响应式连接"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "score": 82,
    "bondDelta": 2,
    "bondValue": 12,
    "classmateReply": "谢谢你，我好像懂了。原来重点是解构后断开了原来的响应式引用。",
    "teacherSupplement": "再补一句：实际项目里可以用 toRefs 保留响应式。"
  }
}
```

### 8.6 举手自由提问

普通响应：

```http
POST /api/study/sessions/{sessionId}/ask
```

请求：

```json
{
  "nodeId": "n2",
  "question": "ref 在模板里为什么不用 .value？"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "answer": "模板编译时会自动解包 ref，所以模板里可以直接写 count。script 中不会自动解包，所以要写 count.value。",
    "relatedKeywords": ["模板自动解包", ".value", "script"]
  }
}
```

流式响应：

```http
GET /api/study/sessions/{sessionId}/ask/stream?nodeId=n2&question=xxx
Accept: text/event-stream
```

SSE 事件：

```text
event: delta
data: {"text":"模板编译时会自动解包 ref"}

event: done
data: {"requestId":"ai_123"}
```

### 8.7 获取作业

```http
GET /api/study/sessions/{sessionId}/homework
```

响应：

```json
{
  "code": 200,
  "data": {
    "items": [
      {
        "topicId": 101,
        "title": "Vue3 中 ref 和 reactive 的核心区别是什么？",
        "body": "请从使用场景、访问方式、响应式边界三个角度回答。",
        "tags": ["Vue3", "响应式"],
        "difficulty": "easy"
      }
    ]
  }
}
```

### 8.8 提交作业

```http
POST /api/study/sessions/{sessionId}/homework/{topicId}/submit
```

请求：

```json
{
  "answerText": "ref 适合基本类型，script 里需要 .value；reactive 适合对象，解构会丢响应式，可以用 toRefs。"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "score": 92,
    "hitKeywords": ["基本类型", ".value", "对象", "解构", "toRefs"],
    "missKeywords": ["Proxy"],
    "feedback": "答案已经比较完整，建议补上 reactive 基于 Proxy 代理对象。",
    "aiReview": "这题面试官想听三个点：使用场景、访问方式、响应式边界。你的答案覆盖了大部分，只差 Proxy 这个底层关键词。",
    "standardAnswer": "ref 常用于基本类型或需要整体替换的值，script 中通过 .value 访问；reactive 常用于对象或数组，基于 Proxy 代理。reactive 解构会丢响应式，通常配合 toRefs。",
    "needAiReview": false
  }
}
```

### 8.9 获取复盘

```http
GET /api/study/sessions/{sessionId}/review
```

响应：

```json
{
  "code": 200,
  "data": {
    "averageScore": 84,
    "bondValue": 15,
    "weakTags": ["Proxy", "computed"],
    "summary": "本节你已经掌握 ref 和 reactive 的基本区别，建议下一节补 computed 与 watch。",
    "nextCourse": {
      "courseId": 2,
      "title": "computed 与 watch 专项"
    }
  }
}
```

## 9. 后台管理接口

### 9.1 手动生成课程材料

```http
POST /api/admin/ai/course/{courseId}/generate
```

请求：

```json
{
  "force": false,
  "promptVersion": "lesson-v1"
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "taskId": "mq_202607050001",
    "status": "pending"
  }
}
```

### 9.2 查询生成状态

```http
GET /api/admin/ai/material/{materialId}
```

### 9.3 配置 AI 供应商

```http
POST /api/admin/ai/providers
```

请求：

```json
{
  "providerCode": "deepseek",
  "providerName": "DeepSeek",
  "protocol": "openai-compatible",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-***",
  "defaultModel": "deepseek-chat",
  "supportStream": true,
  "supportJson": true,
  "priority": 10,
  "enabled": true
}
```

### 9.4 测试 AI 供应商

```http
POST /api/admin/ai/providers/{providerCode}/test
```

响应：

```json
{
  "code": 200,
  "data": {
    "success": true,
    "latencyMs": 1280,
    "model": "deepseek-chat",
    "reply": "连接成功"
  }
}
```

## 10. 内部 AI 网关接口

这些接口可以只给后端内部调用。

### 10.1 普通对话

```http
POST /internal/ai/chat
```

请求：

```json
{
  "scene": "free-ask",
  "providerCode": "deepseek",
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是 AI 老师。"
    },
    {
      "role": "user",
      "content": "解释一下 ref。"
    }
  ],
  "temperature": 0.4,
  "maxTokens": 800
}
```

### 10.2 结构化输出

```http
POST /internal/ai/structured
```

请求：

```json
{
  "scene": "homework-review",
  "schemaName": "HomeworkReviewResult",
  "messages": []
}
```

响应：

```json
{
  "score": 86,
  "hitKeywords": ["ref", ".value"],
  "missKeywords": ["Proxy"],
  "feedback": "答案方向正确，但缺少底层原理。",
  "suggestion": "补充 reactive 基于 Proxy 的表达。"
}
```

### 10.3 流式对话

```http
GET /internal/ai/chat/stream
Accept: text/event-stream
```

## 11. Prompt 模板设计

### 11.1 课程生成 Prompt

输入：

- 课程标题。
- 题目列表。
- 标准答案。
- 标签。
- 角色设定。
- 输出 JSON schema。

要求：

- 输出节点化脚本。
- 每 2 到 3 个 lecture 后插入 checkpoint。
- 每节课最多插入 1 到 2 个 classmate 节点。
- 不要输出过长段落。
- 每个节点适合一个手机屏幕阅读。

### 11.2 作业讲评 Prompt

要求：

- 先判断用户答案是否覆盖关键点。
- 输出分数、命中关键词、遗漏关键词。
- 先鼓励，再指出问题。
- 给出面试中更好的表达。
- 输出 JSON，方便前端渲染。

### 11.3 举手提问 Prompt

要求：

- 只围绕当前课程、当前知识点回答。
- 不要展开无关知识。
- 回答控制在 150 到 300 字。
- 如果问题超出范围，先简单回答，再引导回当前课。

## 12. 前端交互设计

### 12.1 快速学习模式

适合想刷题的用户。

保留：

- AI 老师讲课。
- 课堂提问。
- 作业讲评。

关闭：

- AI 同桌。
- 羁绊值。
- 剧情化台词。

### 12.2 沉浸学习模式

适合提高学习积极性。

开启：

- AI 老师。
- AI 同桌。
- 课间互助。
- 羁绊值。
- 夸赞和安慰。

### 12.3 UI 原则

- 页面首先是学习工具，不是恋爱游戏。
- 角色占页面 15% 到 25% 视觉权重即可。
- 对话框像游戏，但信息密度要适合刷题。
- 作业区要清晰、稳重，不能被角色抢走注意力。
- 同桌互动每节课最多 1 到 2 次。

## 13. 快速 demo 搭建清单

### 13.1 最小文件

```text
interactive-quiz-demo/
  index.html
  course.mock.json
  assets/
    teacher.webp
    classmate.webp
```

### 13.2 最小 JSON

```json
{
  "courseId": 1,
  "title": "Vue3 响应式入门",
  "nodes": [
    {
      "type": "lecture",
      "speaker": "teacher",
      "text": "ref 更像一个带 .value 的盒子。"
    },
    {
      "type": "checkpoint",
      "speaker": "teacher",
      "question": "ref 在 script 中为什么要用 .value？",
      "keywords": [".value", "容器", "基本类型"]
    },
    {
      "type": "classmate",
      "speaker": "classmate",
      "question": "我还是不懂 reactive 解构为什么会丢响应式，你能讲给我听吗？",
      "keywords": ["解构", "响应式丢失", "toRefs"]
    }
  ],
  "homework": [
    {
      "topicId": 101,
      "title": "ref 和 reactive 的区别是什么？",
      "keywords": ["基本类型", "对象", ".value", "Proxy", "toRefs"]
    }
  ]
}
```

### 13.3 最小评分函数

```js
function scoreAnswer(text, keywords) {
  const normalized = text.toLowerCase()
  const hits = keywords.filter((item) => normalized.includes(item.toLowerCase()))
  const score = Math.min(100, Math.round((hits.length / keywords.length) * 85) + 15)
  return {
    score,
    hits,
    misses: keywords.filter((item) => !hits.includes(item))
  }
}
```

### 13.4 最小后端版本

```text
SpringBoot 单体即可：

GET  /api/study/courses
POST /api/study/sessions
GET  /api/study/sessions/{id}/script
POST /api/study/sessions/{id}/nodes/{nodeId}/submit
POST /api/study/sessions/{id}/homework/{topicId}/submit
```

这个阶段先不要拆复杂微服务。

## 14. 可能问题与处理

### 14.1 AI 成本高

处理：

- 固定内容预生成。
- 热门课程 Redis 缓存。
- 规则评分优先。
- 实时 AI 只处理开放问题。

### 14.2 用户觉得慢

处理：

- 预生成课程脚本。
- 自由提问用 SSE 流式输出。
- 作业提交先返回规则评分，再异步补充深度讲评。

### 14.3 角色太抢戏

处理：

- 提供快速模式。
- 同桌互动限制频率。
- 羁绊值弱化，不做恋爱化表达。

### 14.4 AI 输出不稳定

处理：

- 讲课脚本需要后台审核。
- 实时输出限制上下文。
- 结构化输出必须 JSON 校验。
- 失败时降级到模板讲解。

### 14.5 多供应商维护复杂

处理：

- 先统一 OpenAI 兼容协议。
- 特殊协议再单独写 Adapter。
- 供应商配置放数据库。
- 业务层只认 `AiGatewayService`。

## 15. 验收标准

P0 demo 验收：

- 用户可以选择课程。
- AI 老师能一句一句讲课。
- 中途出现课堂提问。
- 用户回答后有即时反馈。
- AI 同桌能向用户请教。
- 答对增加羁绊值，答错也有安慰和讲解。
- 讲完能进入作业。
- 作业提交后能逐题讲评。

P1 业务版验收：

- 课程脚本来自后端。
- 课程材料可预生成。
- Redis 能缓存热门课程。
- RabbitMQ 能异步生成材料。
- 至少接入 2 个 AI Provider。
- AI 失败时有降级方案。
- 学习记录能落库。

当前 `xiaorong-teacher-assistant` 后端已实现：后台生成接口先创建 `ai_lesson_material.status=pending` 并返回 `taskId/materialId`，再通过 RabbitMQ 直连交换机 `xiaorong.ai.direct`、队列 `xiaorong.ai.lesson-material.generate`、路由键 `GENERATE_LESSON_MATERIAL` 异步消费；监听器复用固定模板 Provider，按同一 SHA-256 内容哈希更新材料并刷新 Redis。自由提问 `ask()` 会落 `ai_study_record.node_type=free_ask`，流式提问暂不落库以避免保存半截内容。Docker Compose 提供 MySQL、Redis、RabbitMQ Management 与应用四个服务。

## 16. 每用户 API Key 与模型偏好

当前实现把“系统 Provider 目录”和“用户个人凭据”分离：管理员/部署配置决定可信 `provider_code`、协议和 Base URL，登录用户只能为已启用 Provider 保存自己的 Key，并为自己选择模型。业务请求仍只依赖 `AiGatewayService`，由 `DynamicAiGatewayService` 根据 `AuthContext` 动态路由。

### 16.1 数据模型

```sql
create table ai_user_provider_secret (
  user_id bigint not null,
  provider_code varchar(64) not null,
  encrypted_api_key text not null,
  encryption_iv varchar(64) not null,
  key_version varchar(20) not null,
  api_key_last_four varchar(16) not null,
  create_time datetime not null,
  update_time datetime not null,
  primary key (user_id, provider_code)
);

create table ai_user_ai_preference (
  user_id bigint not null primary key,
  provider_code varchar(64) not null,
  model varchar(200) not null,
  update_time datetime not null
);
```

密钥使用部署环境中的 `XIAORONG_AI_SECRET_MASTER_KEY`（Base64 编码的 32 字节随机值）执行 AES-256-GCM 加密。每次加密生成独立 12 字节 IV，数据库只保留密文、IV、版本和末四位；完整 Key 不进入响应、日志或浏览器持久化。

### 16.2 用户接口

所有接口都要求登录，`userId` 只从 `AuthContext.requireUserId()` 获取。

| 方法 | 路径 | 用途 | 安全约束 |
|---|---|---|---|
| `GET` | `/api/user/ai/settings` | 查询可信 Provider、个人配置状态和当前偏好 | 只返回脱敏 Key，不返回密文/IV |
| `PUT` | `/api/user/ai/providers/{providerCode}/secret` | 保存或替换当前用户 Key | 请求只含 `apiKey`；AES-GCM 加密后落库 |
| `DELETE` | `/api/user/ai/providers/{providerCode}/secret` | 删除当前用户 Key | 同 Provider 偏好同步撤销 |
| `POST` | `/api/user/ai/providers/{providerCode}/test` | 使用当前用户 Key 测试连接 | 上游异常统一脱敏，禁止回传响应体 |
| `GET` | `/api/user/ai/providers/{providerCode}/models` | 代理可信 Provider 的 `/models` | Base URL 只能来自后端白名单配置 |
| `PUT` | `/api/user/ai/preference` | 保存当前用户 Provider/模型 | 必须已配置该 Provider 的个人 Key；模型名最长 200 |

保存 Key：

```http
PUT /api/user/ai/providers/bailian/secret
Content-Type: application/json
Authorization: Bearer xr_xxx

{
  "apiKey": "用户自己的 API Key"
}
```

切换模型：

```http
PUT /api/user/ai/preference
Content-Type: application/json
Authorization: Bearer xr_xxx

{
  "providerCode": "bailian",
  "model": "qwen-plus"
}
```

### 16.3 运行时路由与降级

- 有登录用户且个人 Provider/模型配置完整：解密该用户 Key，仅在服务端发起上游请求时使用，并强制覆盖前端请求携带的 Provider/模型。
- 有登录用户但未配置、持久化未启用或主密钥不可用：使用 Mock，不允许回退到系统 Key。
- 个人密文损坏或解密失败：返回安全错误，不静默切到系统 Key。
- 无用户上下文的后台任务：只有 `xiaorong.ai.real-enabled=true` 时才使用系统 Provider，否则使用 Mock。
- 前端 `/admin/runtime` 不提供自定义 Base URL；模型既可从 `/models` 获取，也可手工填写以支持新模型立即切换。
## 17. 官方资料参考

- OpenAI API Responses 文档：https://platform.openai.com/docs/api-reference/responses
- Spring AI 官方文档：https://docs.spring.io/spring-ai/reference/
- 阿里云百炼 OpenAI 兼容文档：https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope
- DeepSeek API 文档：https://api-docs.deepseek.com/
- Anthropic Messages API 文档：https://docs.anthropic.com/en/api/messages
