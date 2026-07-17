# 可见 AI 课堂接入实施计划

> 日期：2026-07-16  
> 状态：已按单线程方式实施完成；用户要求不启用多智能体，以避免额外内存占用

**目标：** 让个人 API Key 真正驱动课堂 AI，并在前端清晰展示自由提问、低分深度讲评、个性化建议和 SSE 状态。

**架构：** 个人 AI 路由层从加密 Secret 与偏好解析真实 Provider；学习 AI 层返回可公开的运行元数据并提供首页建议；Vue 前端把固定评分和 AI 内容拆成明确可见的交互区块。所有实施、检查和验收均在当前任务中单线程完成。

**技术栈：** Java 17、Spring Boot、JUnit 5、Vue 3、Pinia、TypeScript、Node test、Docker Compose。

---

### Task 1：修复个人 API Key 保存后仍走 Mock

- [x] 增加失败测试：配置 Secret 但没有显式偏好时，仍能解析可信 Provider/default model。
- [x] 实现确定性的已配置 Secret 回退，按可信 Provider 优先级选择。
- [x] `/admin/runtime` 保存 Secret 后同步保存当前 Provider/模型偏好。
- [x] 模型为空时采用 Provider 默认模型；支持上游模型列表和手工模型名。
- [x] 保存后清空 Key，查询只返回末四位，不在浏览器持久化明文。
- [x] 运行目标测试和完整后端测试。

### Task 2：后端学习 AI 可见元数据、建议和 Token 翻倍

- [x] free-ask/deep-review/interview-follow-up/personal-review 上限调整为 1000/800/600/700。
- [x] free-ask 回答字数约束调整为 600 字，服务端仅保留最近 3 轮。
- [x] Ask、SSE done、深度讲评、建议和复盘返回安全的 `providerCode/model/degraded`。
- [x] 元数据清洗拒绝 Secret、Authorization 等敏感字段。
- [x] 新增 `/api/study/overview/advice`，返回当前用户 2～3 条建议及学习统计。
- [x] 建议按用户与学习数据指纹缓存；学习数据变化后重新生成。
- [x] 复盘 `nextActions` 使用 AI 建议解析结果，`free_ask` 不参与平均分。
- [x] Mock 与 MySQL/Redis 实现保持一致。

### Task 3：前端真实可见 AI 交互

- [x] 扩展 API 类型和 Store，运行元数据保持可空，不伪造 Provider/模型。
- [x] 所有课堂节点默认显示 AskBox，并按节点保留多轮历史。
- [x] 固定评分与自由 AI 回答使用不同区块。
- [x] POST SSE 追加 `delta`，消费 `done`；缺少 `done`、无正文或传输失败时回退普通 POST。
- [x] 展示连接中、生成中、完成、失败以及真实/演示/降级状态。
- [x] 深度讲评轮询最长 60 秒，支持超时和失败重试。
- [x] HomeView 展示当前用户薄弱点和个性化建议；ReviewView 展示 AI `nextActions` 与运行状态。
- [x] 前端测试、类型检查和生产构建通过。

### Task 4：文档和集成验证

- [x] 更正完成边界：4.1、4.2、4.4、4.5、4.6 完成；4.3 暂缓。
- [x] 记录新 API、安全元数据、Token 上限、Mock 可见性和 Secret 回退行为。
- [x] 后端 `mvn -q test`、`mvn -q -DskipTests package` 通过。
- [x] 前端 `npm test`（11/11）、`npm run type-check`、`npm run build` 通过。
- [x] 使用最新代码重建 Docker `app/frontend`；依赖容器 healthy，应用启动成功。
- [x] 8088 代理回归：根页面 200；未登录 runtime/courses API 401，而非 502。
- [x] 源码编码扫描无 UTF-8 BOM、无 连续问号乱码。
- [x] 按用户要求取消独立验收 Agent，改为当前任务内单线程执行完整验收。

## 验收结论

批准方案 B 已落地。仅保存个人 Secret 后不再因为缺少偏好静默走 Mock；课堂、首页和复盘能明确展示 AI 内容及真实/降级状态；Secret 仍停留在加密存储安全边界内。4.3 面试官功能保持暂缓。