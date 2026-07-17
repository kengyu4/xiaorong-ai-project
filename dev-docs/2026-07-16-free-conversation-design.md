# 自由对话与用户薄弱点概览设计

> 日期：2026-07-16  
> 状态：4.1、4.2、4.4、4.5、4.6 已实施；4.3 面试官完整业务暂缓  
> 对应任务：`待做任务清单.md` 第四节

## 1. 目标

补齐学习流程中的可见 AI 能力，将 `/app/courses` 首页硬编码的 `Proxy` 替换为当前登录用户的真实薄弱点数据，并确保个人 API Key 保存后能够真正驱动学习 AI，而不是因缺少偏好配置静默进入 Mock。

## 2. 已确认的用户体验

- 首页“薄弱点”卡片展示当前用户最近学习记录中出现频率最高的遗漏关键词，例如 `Proxy`；没有学习记录时显示“暂无”。
- 首页同时展示“小绒老师的学习建议”，依据当前用户薄弱标签、平均分和最近课程生成 2～3 条可执行建议。
- 所有课堂节点默认展示“问小绒老师 AI”，按节点保留多轮问题和回答，而不是只在 checkpoint 节点显示折叠入口。
- 页面明确显示“真实 AI · Provider · 模型”或“演示/降级回复”，固定评分反馈与自由 AI 回答分区展示。
- 课堂提问优先使用 POST SSE；传输失败、缺少 `done` 或无正文时自动回退普通 POST。
- 低于 70 分的回答先立即返回关键词评分，再异步生成深度讲评；前端最多轮询 60 秒并提供重试。
- 复盘保留固定统计数据，并将 AI 生成的 2～3 条建议作为 `nextActions`；AI 失败时退回固定建议。
- 面试官页面和完整追问流程不在本次实施范围，仅保留已有后端基础与翻倍后的预算上限。

## 3. 个人 AI 路由与安全边界

### 3.1 Secret 与偏好

- `/admin/runtime` 保存个人 Secret 时，同步保存当前 Provider 和模型偏好；模型为空时使用该 Provider 默认模型。
- 后端增加容错：若用户已有一个或多个加密 Secret、但偏好缺失或失效，则按可信 Provider 的优先级选择第一个已配置 Provider及其默认模型。
- 上游 Base URL 只来自后端可信 Provider 注册表；用户不能提交任意 URL。
- 浏览器只短暂持有明文 API Key，保存成功立即清空，不写入 localStorage、sessionStorage、Cookie 或持久化 Store。
- API Key 使用服务端 AES-256-GCM 加密存储，查询仅返回末四位脱敏信息。

### 3.2 可公开运行元数据

学习 AI 响应只返回：

- `providerCode`
- `model`
- `degraded`

不得返回 API Key、密文、IV、主密钥、Authorization、上游请求头、上游原始错误或可推导 Secret 的元数据。元数据缺失时前端保持 `null`，不得自行伪造 Provider/模型。

## 4. 后端设计

### 4.1 场景编排服务

`StudyAiConversationService` 统一封装：

1. `free-ask`：当前节点 + 最近 3 轮服务端对话，上限 1000 token，回答约束 600 字。
2. `deep-review`：题目、答案、命中/遗漏关键词，上限 800 token。
3. `interview-follow-up`：上限 600 token；只保留已有后端基础，不把 4.3 计为已完成。
4. `personal-review`：平均分、课程、薄弱标签，上限 700 token。

业务 Controller 不允许客户端直接指定 Provider、模型、Base URL 或 maxTokens；统一走当前用户的安全 AI 设置。

### 4.2 上下文

- 每个 `userId + sessionId + nodeId` 保存最多 6 条历史消息，即最近 3 轮 user/assistant。
- 普通 `/ask` 在 JDBC 模式将问答作为 `free_ask` 学习记录写入 `ai_study_record`，但 `free_ask` 不参与平均分计算。
- 流式上下文当前不跨应用重启恢复；如需恢复，应新增专用消息表，而不是让浏览器提交任意历史。
- 生成提示词只加入当前节点和最近 3 轮，不传整套题库或用户资料。

### 4.3 低分异步讲评

- 节点或作业关键词评分 `<70` 时创建异步任务。
- 提交接口立即返回 `aiReviewTaskId` 和 `needAiReview=true`。
- `GET /api/study/sessions/{sessionId}/ai-reviews/{taskId}` 返回 `pending/completed/failed`。
- 任务归属校验当前用户和 session。
- AI 失败时返回关键词遗漏 + 标准答案的安全降级讲评，并标记 `degraded=true`。

### 4.4 个性化建议与复盘

- `GET /api/study/overview/advice` 基于当前用户学习数据生成 2～3 条建议。
- 建议按 `userId + 学习数据指纹` 缓存；学习记录变化后自动重新生成，避免首页每次刷新消耗 Token。
- 首页和 session 复盘共用建议解析逻辑；复盘 `nextActions` 不再使用固定数组。
- 平均分计算排除 `free_ask` 记录。

### 4.5 Token 预算

`AiTokenBudgetService` 强制覆盖场景预算，客户端无法抬高：

| 场景 | 单次输出上限 |
|------|-------------|
| free-ask | 1000 token |
| deep-review | 800 token |
| interview-follow-up | 600 token（4.3 暂缓） |
| personal-review | 700 token |
| default | 600 token |

服务记录按日期、用户聚合的请求数、prompt/completion token 和降级次数；达到告警阈值时标记 warning，达到硬上限时返回固定降级内容。

## 5. API 变化

- `GET /api/study/overview`：首页用户概览，含 `topWeakTag`、`weakTags`、`weakTagCount`、`completedCount`。
- `GET /api/study/overview/advice`：当前用户 2～3 条建议、学习统计以及安全运行元数据。
- `POST /api/study/sessions/{sessionId}/ask`：完整回答、上下文记录与安全运行元数据。
- `POST /api/study/sessions/{sessionId}/ask/stream`：SSE 事件 `delta`、`done`；`done` 携带安全运行元数据。
- `GET /api/study/sessions/{sessionId}/ai-reviews/{taskId}`：异步讲评状态与安全运行元数据。
- `GET /api/study/sessions/{sessionId}/review`：固定统计 + AI 建议 `nextActions` + 安全运行元数据。
- `GET /api/study/ai/budget`：当前用户当日 Token 预算状态。

已有 `POST /api/study/sessions/{sessionId}/interview/follow-up` 仅视为后端基础代码，不代表 4.3 页面和完整业务已完成。

## 6. 前端设计

- `RuntimeStatusView.vue` 保存 Secret 后同步偏好；支持可信 Provider 切换、模型列表和手工模型名，Key 不回显。
- `HomeView.vue` 加载 overview 和 advice，用真实数据替换硬编码 `Proxy`，显示加载、无数据、成功与降级状态。
- `AskBox.vue` 在所有课堂节点默认显示，展示当前节点的多轮历史与连接状态。
- `study.ts` / Store 要求 SSE 必须收到 `done` 且存在正文，否则回退普通 POST。
- `NodeResult.vue` 对低分讲评显示 pending/completed/failed，轮询最长 60 秒并提供“重新获取”。
- `ReviewView.vue` 展示 AI 建议及真实/降级运行状态。

## 7. 安全与降级

- 所有接口要求登录；session、任务、记录、缓存均按 userId 隔离。
- SSE 使用 POST + Authorization + JSON，不把问题放入 URL。
- 不记录 API Key、Authorization 或完整敏感 Prompt。
- AI 超时、额度耗尽、Provider 不可用或 Secret 解密失败时，学习主流程仍可使用固定内容继续，并明确标记降级。
- Mock/降级不能冒充真实 AI。

## 8. 验收结果（2026-07-16）

1. 首页源码不再硬编码 `<strong>Proxy</strong>`，接口和页面展示当前用户薄弱点及建议。
2. 仅保存个人 Secret 后，即使偏好缺失，后端也能选择已配置可信 Provider/default model；保存 UI 同时写入偏好。
3. 所有课堂节点可见自由提问，保留最近 3 轮，free-ask 上限为 1000 token。
4. 低分深度讲评前端轮询最长 60 秒且可重试，上限为 800 token。
5. 个性化建议上限为 700 token，首页和复盘均可见。
6. SSE 使用 POST，消费 `delta` / `done`，失败回退普通 POST。
7. 页面明确展示真实 Provider/模型或演示/降级状态，响应不包含 Secret。
8. 4.3 明确为暂缓，只有 600 token 预算和既有后端基础。
9. 后端测试、前端测试、类型检查、生产构建及 Docker 8088 代理链路通过；未登录 API 返回 401 而不是 502。

### SSE and XiaoRong persona refinement

- `free-ask` now consumes provider deltas through a callback-oriented gateway path; it forwards each upstream delta to the browser immediately, then persists the completed round only on the terminal event.
- The OpenAI-compatible adapter sends `stream: true` to `/chat/completions`, parses each upstream `data:` SSE frame, and forwards `choices[0].delta.content` without waiting for the complete message.
- The free-conversation system prompt fixes the role as XiaoRong teacher: conclusion first, one or two reasons/minimal example, no greeting/repetition/filler, three to five sentences and within 180 Chinese characters.
- Runtime metadata is still allowlisted server-side. Missing `providerCode` or `model` stays `null` in the browser instead of being fabricated as a mock provider.
- Nginx disables buffering and raises the upstream read timeout for `/api/`, so Docker proxying does not coalesce the event stream.



### ???? Spring AI ?????2026-07-16?

- ?? Provider ??????? `dashscope`?? `DashScopeAiProviderAdapter` ?? Spring AI Alibaba ? `DashScopeChatModel.stream(Prompt)` ??????????? `java.net.http.HttpClient`?
- `DefaultDashScopeChatModelFactory` ????????????????????????????? API Key??????Token ??????????? Key???? Key ?? SSE??????????
- ???????? SSE ???????????? `blockLast()`??????? POST SSE?????????? `SseEmitter`????? Reactor ??? `subscribe()`??????????????????????
- ?????????? `ready` ? ?? `delta` ? `done`?Controller ? Nginx ?????/????? `fetch` ?? `cache: 'no-store'` ????????
- ?? Prompt ?????????? Markdown ??????????????????? `**`???????????????? AI ???? Markdown ????? API Key ? `****???` ?????????
- DeepSeek ? OneAPI ???? OpenAI-compatible adapter????? Provider ??? `/admin/runtime` ????????????????????
